<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreTransactionRequest;
use App\Http\Resources\Api\V1\TransactionResource;
use App\Models\Account;
use App\Models\Transaction;
use App\Models\WalletSyncResource;
use Carbon\CarbonImmutable;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class TransactionController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'account_id' => ['nullable', 'string', 'max:255'],
            'updated_since' => ['nullable', 'date'],
            'cursor' => ['nullable', 'string'],
            'per_page' => ['nullable', 'integer', 'between:1,200'],
        ]);
        if (isset($validated['account_id'])) {
            $request->user()->accounts()->whereKey($validated['account_id'])->firstOrFail();
        }
        $transactions = Transaction::query()
            ->where('user_id', $request->user()->id)
            ->when($validated['account_id'] ?? null, fn ($query, string $accountId) => $query->where('account_id', $accountId))
            ->when($validated['updated_since'] ?? null, fn ($query, string $date) => $query->where('updated_at', '>', $date))
            ->orderBy('id')
            ->cursorPaginate($validated['per_page'] ?? 200, ['*'], 'cursor', $validated['cursor'] ?? null);

        return response()->json([
            'data' => TransactionResource::collection($transactions->items())->resolve($request),
            'meta' => ['next_cursor' => $transactions->nextCursor()?->encode()],
        ]);
    }

    public function store(StoreTransactionRequest $request): JsonResponse
    {
        $validated = $request->validated();
        if (isset($validated['category_id'])) {
            $categoryExists = (new WalletSyncResource)->setTable('categories')->newQuery()
                ->where('user_id', $request->user()->id)
                ->whereKey($validated['category_id'])
                ->where('is_deleted', false)
                ->exists();
            if (! $categoryExists) {
                throw ValidationException::withMessages([
                    'category_id' => ['La categoría no pertenece al usuario o fue eliminada.'],
                ]);
            }
        }

        try {
            [$transaction, $created] = DB::transaction(function () use ($request, $validated): array {
                $account = Account::query()
                    ->where('user_id', $request->user()->id)
                    ->whereKey($validated['account_id'])
                    ->lockForUpdate()
                    ->firstOrFail();

                $existing = Transaction::query()
                    ->where('user_id', $request->user()->id)
                    ->where('idempotency_key', $validated['idempotency_key'])
                    ->first();

                if ($existing) {
                    $this->ensureSameOperation($existing, $validated);

                    return [$existing, false];
                }

                if ($account->currency !== $validated['currency']) {
                    throw ValidationException::withMessages([
                        'currency' => ['La moneda del movimiento debe coincidir con la moneda de la cuenta.'],
                    ]);
                }

                $transaction = $account->transactions()->create([
                    'user_id' => $request->user()->id,
                    'idempotency_key' => $validated['idempotency_key'],
                    'amount' => $validated['amount'],
                    'currency' => $validated['currency'],
                    'description' => $validated['description'] ?? null,
                    'category_id' => $validated['category_id'] ?? null,
                    'occurred_at' => $validated['timestamp'],
                    'status' => $validated['status'],
                ]);

                $account->increment('balance', $validated['amount']);

                return [$transaction, true];
            });
        } catch (UniqueConstraintViolationException $exception) {
            $transaction = Transaction::query()
                ->where('user_id', $request->user()->id)
                ->where('idempotency_key', $validated['idempotency_key'])
                ->first();

            if (! $transaction) {
                throw $exception;
            }

            $this->ensureSameOperation($transaction, $validated);
            $created = false;
        }

        return response()->json([
            'data' => (new TransactionResource($transaction))->resolve($request),
        ], $created ? 201 : 200);
    }

    /**
     * Corrige un movimiento ya registrado. store() es deliberadamente inmutable -su
     * clave de idempotencia protege al reintento de corromper el importe-, asi que
     * editar necesita su propia puerta.
     *
     * El saldo de la cuenta se mueve por la diferencia, nunca se recalcula: sumar el
     * delta bajo bloqueo es lo unico que no se descuadra si entran dos ediciones a la vez.
     */
    public function update(Request $request, string $transaction): JsonResponse
    {
        $transaction = $this->resolveOwned($request, $transaction);

        $validated = $request->validate([
            'amount' => ['sometimes', 'integer', 'not_in:0', 'between:-9000000000000000,9000000000000000'],
            'description' => ['sometimes', 'nullable', 'string', 'max:500'],
            'category_id' => ['sometimes', 'nullable', 'string', 'max:100'],
            'timestamp' => ['sometimes', 'date'],
            'status' => ['sometimes', Rule::in(['pending', 'completed', 'cancelled'])],
        ]);

        $this->assertCategoryBelongsToUser($request, $validated['category_id'] ?? null);

        $updated = DB::transaction(function () use ($request, $transaction, $validated): Transaction {
            $account = Account::query()
                ->where('user_id', $request->user()->id)
                ->whereKey($transaction->account_id)
                ->lockForUpdate()
                ->firstOrFail();

            $fresh = $transaction->newQuery()->whereKey($transaction->getKey())->lockForUpdate()->firstOrFail();
            $delta = array_key_exists('amount', $validated) ? $validated['amount'] - $fresh->amount : 0;

            $fresh->fill([
                'amount' => $validated['amount'] ?? $fresh->amount,
                'description' => array_key_exists('description', $validated) ? $validated['description'] : $fresh->description,
                'category_id' => array_key_exists('category_id', $validated) ? $validated['category_id'] : $fresh->category_id,
                'occurred_at' => $validated['timestamp'] ?? $fresh->occurred_at,
                'status' => $validated['status'] ?? $fresh->status,
            ])->save();

            if ($delta !== 0) {
                $account->increment('balance', $delta);
            }

            return $fresh;
        });

        return response()->json([
            'data' => (new TransactionResource($updated))->resolve($request),
        ]);
    }

    /**
     * Elimina el movimiento y devuelve su importe al saldo. Un borrado repetido
     * responde 204 igual: la app reintenta su cola y no debe atascarse porque el
     * servidor ya lo hubiera aplicado.
     */
    public function destroy(Request $request, string $transaction): JsonResponse
    {
        $transaction = $this->resolveOwned($request, $transaction);

        DB::transaction(function () use ($request, $transaction): void {
            $account = Account::query()
                ->where('user_id', $request->user()->id)
                ->whereKey($transaction->account_id)
                ->lockForUpdate()
                ->firstOrFail();

            $fresh = $transaction->newQuery()->whereKey($transaction->getKey())->lockForUpdate()->first();
            if (! $fresh) {
                return;
            }

            $account->decrement('balance', $fresh->amount);
            $fresh->delete();
        });

        return response()->json(status: 204);
    }

    /**
     * La app solo conoce el id que ella genero, que viaja como idempotency_key; el id
     * de la fila lo pone el servidor. Se acepta cualquiera de los dos para que un
     * cliente pueda corregir lo que subio sin tener que aprender el id remoto.
     */
    private function resolveOwned(Request $request, string $key): Transaction
    {
        return Transaction::query()
            ->where('user_id', $request->user()->id)
            ->where(fn ($query) => $query->where('idempotency_key', $key)->orWhere('id', $key))
            ->firstOr(fn () => abort(404));
    }

    private function assertCategoryBelongsToUser(Request $request, ?string $categoryId): void
    {
        if ($categoryId === null) {
            return;
        }

        $exists = (new WalletSyncResource)->setTable('categories')->newQuery()
            ->where('user_id', $request->user()->id)
            ->whereKey($categoryId)
            ->where('is_deleted', false)
            ->exists();

        if (! $exists) {
            throw ValidationException::withMessages([
                'category_id' => ['La categoría no pertenece al usuario o fue eliminada.'],
            ]);
        }
    }

    private function ensureSameOperation(Transaction $transaction, array $validated): void
    {
        $sameOperation = $transaction->account_id === $validated['account_id']
            && $transaction->amount === $validated['amount']
            && $transaction->currency === $validated['currency']
            && $transaction->description === ($validated['description'] ?? null)
            && $transaction->category_id === ($validated['category_id'] ?? null)
            && $transaction->status === $validated['status']
            && $transaction->occurred_at->equalTo(CarbonImmutable::parse($validated['timestamp']));

        abort_unless($sameOperation, 409, 'La clave de idempotencia ya pertenece a otra operación.');
    }
}

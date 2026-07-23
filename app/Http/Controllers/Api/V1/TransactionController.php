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

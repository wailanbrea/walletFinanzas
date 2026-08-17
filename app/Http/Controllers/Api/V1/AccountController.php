<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreAccountRequest;
use App\Http\Resources\Api\V1\AccountResource;
use App\Models\Account;
use App\Models\Transaction;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AccountController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'updated_since' => ['nullable', 'date'],
            'cursor' => ['nullable', 'string'],
            'per_page' => ['nullable', 'integer', 'between:1,200'],
        ]);
        $accounts = $request->user()->accounts()
            ->when($validated['updated_since'] ?? null, fn ($query, string $date) => $query->where('updated_at', '>', $date))
            ->orderBy('id')
            ->cursorPaginate($validated['per_page'] ?? 200, ['*'], 'cursor', $validated['cursor'] ?? null);

        return response()->json([
            'data' => AccountResource::collection($accounts->items())->resolve($request),
            'meta' => ['next_cursor' => $accounts->nextCursor()?->encode()],
        ]);
    }

    public function store(StoreAccountRequest $request): AccountResource
    {
        $validated = $request->validated();
        if (isset($validated['id'])) {
            abort_if(
                Account::query()->whereKey($validated['id'])->where('user_id', '!=', $request->user()->id)->exists(),
                409,
                'La cuenta ya pertenece a otro usuario.',
            );
            $account = $request->user()->accounts()->updateOrCreate(['id' => $validated['id']], $validated);
        } else {
            $account = $request->user()->accounts()->create($validated);
        }

        return new AccountResource($account);
    }

    public function consistency(Request $request, string $accountId): JsonResponse
    {
        $account = $request->user()->accounts()->find($accountId);

        abort_if(is_null($account), 404, 'Account not found.');

        $summary = Transaction::query()
            ->where('account_id', $account->id)
            ->where('status', 'completed')
            ->selectRaw('COALESCE(SUM(amount), 0) as calculated_balance, COUNT(*) as transaction_count')
            ->first();

        $storedBalance = (int) $account->balance;
        $calculatedBalance = (int) $summary->calculated_balance;
        $openingBalance = (int) $account->opening_balance;

        // La linea base se congela en la migracion: opening = balance actual
        // menos la suma de transacciones completadas en ese instante. Cualquier
        // divergencia posterior indica corrupcion real.
        $unexplainedDifference = $storedBalance - $openingBalance - $calculatedBalance;

        return response()->json([
            'account_id' => $account->id,
            'account_name' => $account->name,
            'stored_balance' => $storedBalance,
            'calculated_balance' => $calculatedBalance,
            'opening_balance' => $openingBalance,
            'unexplained_difference' => $unexplainedDifference,
            'transaction_count' => (int) $summary->transaction_count,
            'consistent' => $unexplainedDifference === 0,
        ]);
    }
}

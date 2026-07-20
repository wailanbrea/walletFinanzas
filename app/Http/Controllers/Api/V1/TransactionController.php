<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreTransactionRequest;
use App\Http\Resources\Api\V1\TransactionResource;
use App\Models\Account;
use App\Models\Transaction;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class TransactionController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        $validated = $request->validate([
            'account_id' => ['required', 'uuid'],
        ]);

        $account = $request->user()
            ->accounts()
            ->whereKey($validated['account_id'])
            ->firstOrFail();

        return TransactionResource::collection(
            $account->transactions()->latest('occurred_at')->get()
        );
    }

    public function store(StoreTransactionRequest $request): TransactionResource
    {
        $validated = $request->validated();

        $transaction = DB::transaction(function () use ($request, $validated): Transaction {
            $account = Account::query()
                ->where('user_id', $request->user()->id)
                ->whereKey($validated['account_id'])
                ->lockForUpdate()
                ->firstOrFail();

            if ($account->currency !== $validated['currency']) {
                throw ValidationException::withMessages([
                    'currency' => ['La moneda del movimiento debe coincidir con la moneda de la cuenta.'],
                ]);
            }

            $transaction = $account->transactions()->create([
                'user_id' => $request->user()->id,
                'amount' => $validated['amount'],
                'currency' => $validated['currency'],
                'description' => $validated['description'] ?? null,
                'category_id' => $validated['category_id'] ?? null,
                'occurred_at' => $validated['timestamp'],
                'status' => $validated['status'],
            ]);

            $account->increment('balance', $validated['amount']);

            return $transaction;
        });

        return new TransactionResource($transaction);
    }
}

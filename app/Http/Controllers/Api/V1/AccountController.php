<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreAccountRequest;
use App\Http\Resources\Api\V1\AccountResource;
use App\Models\Account;
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
}

<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreAccountRequest;
use App\Http\Resources\Api\V1\AccountResource;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class AccountController extends Controller
{
    /**
     * Lista cuentas con paginación por cursor y filtro incremental (?updated_since=)
     * para que el cliente offline-first sincronice solo lo cambiado.
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        $validated = $request->validate([
            'updated_since' => ['sometimes', 'date'],
            'per_page' => ['sometimes', 'integer', 'min:1', 'max:200'],
        ]);

        $query = $request->user()->accounts()
            ->orderBy('updated_at')
            ->orderBy('id');

        if (! empty($validated['updated_since'])) {
            $query->where('updated_at', '>', CarbonImmutable::parse($validated['updated_since']));
        }

        return AccountResource::collection(
            $query->cursorPaginate($validated['per_page'] ?? 100)->withQueryString()
        );
    }

    public function store(StoreAccountRequest $request): AccountResource
    {
        $data = $request->validated();

        // Idempotente por id de cliente: un reintento de sync no duplica la cuenta.
        $account = isset($data['id'])
            ? $request->user()->accounts()->updateOrCreate(['id' => $data['id']], $data)
            : $request->user()->accounts()->create($data);

        return new AccountResource($account);
    }
}

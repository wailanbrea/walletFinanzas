<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreCategoryRequest;
use App\Http\Resources\Api\V1\CategoryResource;
use App\Models\Category;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class CategoryController extends Controller
{
    private const MAX_ACTIVE_CATEGORIES = 200;

    public function index(Request $request): AnonymousResourceCollection
    {
        $validated = $request->validate([
            'updated_since' => ['sometimes', 'date'],
            'per_page' => ['sometimes', 'integer', 'min:1', 'max:200'],
        ]);

        $query = $request->user()->categories()
            ->orderBy('updated_at')
            ->orderBy('id');

        if (! empty($validated['updated_since'])) {
            $query->where('updated_at', '>', CarbonImmutable::parse($validated['updated_since']));
        }

        return CategoryResource::collection(
            $query->cursorPaginate($validated['per_page'] ?? 100)->withQueryString()
        );
    }

    public function store(StoreCategoryRequest $request): JsonResponse
    {
        $data = $request->validated();

        [$category, $created] = DB::transaction(function () use ($request, $data): array {
            // Serializa altas/reactivaciones del mismo usuario para que dos requests
            // concurrentes no puedan superar el límite de categorías activas.
            $user = $request->user()->newQuery()
                ->whereKey($request->user()->getKey())
                ->lockForUpdate()
                ->firstOrFail();
            $category = Category::query()
                ->where('user_id', $user->id)
                ->where('client_id', $data['id'])
                ->lockForUpdate()
                ->first();

            $created = $category === null;
            $willBeActive = ! $data['is_deleted'];
            $wasActive = $category !== null && ! $category->is_deleted;

            if ($willBeActive && ! $wasActive) {
                $activeCount = $user->categories()
                    ->where('is_deleted', false)
                    ->count();

                if ($activeCount >= self::MAX_ACTIVE_CATEGORIES) {
                    throw ValidationException::withMessages([
                        'id' => ['Se alcanzó el límite de 200 categorías activas.'],
                    ]);
                }
            }

            $category ??= new Category([
                'user_id' => $user->id,
                'client_id' => $data['id'],
            ]);
            $category->fill([
                'name' => $data['name'],
                'icon' => $data['icon'],
                'color_hex' => $data['color_hex'],
                'is_deleted' => $data['is_deleted'],
            ]);

            if (! $category->exists || $category->isDirty()) {
                $category->save();
            }

            return [$category, $created];
        });

        return response()->json([
            'data' => (new CategoryResource($category))->resolve($request),
        ], $created ? 201 : 200);
    }
}

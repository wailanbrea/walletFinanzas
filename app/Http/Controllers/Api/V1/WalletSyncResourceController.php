<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\WalletSyncResource;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class WalletSyncResourceController extends Controller
{
    public function categories(Request $request): JsonResponse
    {
        return $this->index($request, 'categories');
    }

    public function storeCategory(Request $request): JsonResponse
    {
        return $this->store($request, 'categories');
    }

    public function budgets(Request $request): JsonResponse
    {
        return $this->index($request, 'budgets');
    }

    public function storeBudget(Request $request): JsonResponse
    {
        return $this->store($request, 'budgets');
    }

    public function goals(Request $request): JsonResponse
    {
        return $this->index($request, 'goals');
    }

    public function storeGoal(Request $request): JsonResponse
    {
        return $this->store($request, 'goals');
    }

    public function debts(Request $request): JsonResponse
    {
        return $this->index($request, 'debts');
    }

    public function storeDebt(Request $request): JsonResponse
    {
        return $this->store($request, 'debts');
    }

    public function plannedPayments(Request $request): JsonResponse
    {
        return $this->index($request, 'planned_payments');
    }

    public function storePlannedPayment(Request $request): JsonResponse
    {
        return $this->store($request, 'planned_payments');
    }

    private function index(Request $request, string $table): JsonResponse
    {
        $validated = $request->validate([
            'updated_since' => ['nullable', 'date'],
            'cursor' => ['nullable', 'string'],
            'per_page' => ['nullable', 'integer', 'between:1,200'],
        ]);
        $model = $this->model($table);
        $page = $model->newQuery()
            ->where('user_id', $request->user()->id)
            ->when($validated['updated_since'] ?? null, fn ($query, string $date) => $query->where('updated_at', '>', $date))
            ->orderBy('id')
            ->cursorPaginate($validated['per_page'] ?? 200, ['*'], 'cursor', $validated['cursor'] ?? null);

        return response()->json([
            'data' => collect($page->items())->map(fn (WalletSyncResource $record) => $this->serialize($record))->values(),
            'meta' => ['next_cursor' => $page->nextCursor()?->encode()],
        ]);
    }

    private function store(Request $request, string $table): JsonResponse
    {
        $validated = $request->validate($this->rules($table));
        if ($table === 'debts') {
            $validated['description'] ??= '';
        }
        if ($table === 'budgets' && ! $validated['is_deleted']) {
            $this->requireOwnedCategory($request, $validated['category_id']);
        }
        if ($table === 'planned_payments') {
            $validated['category_id'] ??= '';
            $accountExists = $request->user()->accounts()->whereKey($validated['account_id'])->exists();
            abort_unless($accountExists, 422, 'La cuenta del pago planificado no pertenece al usuario.');
            if (! $validated['is_deleted'] && $validated['category_id'] !== '') {
                $this->requireOwnedCategory($request, $validated['category_id']);
            }
        }
        $model = $this->model($table);
        $record = $model->newQuery()->updateOrCreate(
            ['id' => $validated['id'], 'user_id' => $request->user()->id],
            $validated,
        );
        if ($table === 'categories' && $validated['is_deleted']) {
            foreach (['budgets', 'planned_payments'] as $dependentTable) {
                $this->model($dependentTable)->newQuery()
                    ->where('user_id', $request->user()->id)
                    ->where('category_id', $validated['id'])
                    ->update(['is_deleted' => true, 'updated_at' => now()]);
            }
        }

        return response()->json(['data' => $this->serialize($record)], $record->wasRecentlyCreated ? 201 : 200);
    }

    private function model(string $table): WalletSyncResource
    {
        return (new WalletSyncResource)->setTable($table);
    }

    private function serialize(WalletSyncResource $record): array
    {
        $data = $record->toArray();
        $data['updated_at'] = $record->updated_at?->toISOString();

        return $data;
    }

    private function requireOwnedCategory(Request $request, string $categoryId): void
    {
        $exists = $this->model('categories')->newQuery()
            ->where('user_id', $request->user()->id)
            ->whereKey($categoryId)
            ->where('is_deleted', false)
            ->exists();
        abort_unless($exists, 422, 'La categoría no pertenece al usuario o fue eliminada.');
    }

    private function rules(string $table): array
    {
        $common = [
            'id' => ['required', 'string', 'max:100'],
            'is_deleted' => ['required', 'boolean'],
        ];

        return $common + match ($table) {
            'categories' => [
                'name' => ['required', 'string', 'max:120'],
                'icon' => ['required', 'string', 'max:80'],
                'color_hex' => ['required', 'regex:/^#[0-9A-Fa-f]{6}$/'],
            ],
            'budgets' => [
                'category_id' => ['required', 'string', 'max:100'],
                'limit_amount' => ['required', 'integer'],
                'spent_amount' => ['required', 'integer'],
                'period' => ['required', 'string', 'max:30'],
            ],
            'goals' => [
                'name' => ['required', 'string', 'max:120'],
                'icon' => ['required', 'string', 'max:80'],
                'target_amount' => ['required', 'integer'],
                'saved_amount' => ['required', 'integer'],
                'target_date' => ['nullable', 'integer'],
                'is_completed' => ['required', 'boolean'],
            ],
            'debts' => [
                'name' => ['required', 'string', 'max:120'],
                'description' => ['nullable', 'string'],
                'direction' => ['required', Rule::in(['I_OWE', 'OWED_TO_ME'])],
                'total_amount' => ['required', 'integer'],
                'paid_amount' => ['required', 'integer'],
                'due_date' => ['nullable', 'integer'],
                'is_closed' => ['required', 'boolean'],
            ],
            'planned_payments' => [
                'name' => ['required', 'string', 'max:120'],
                'account_id' => ['required', 'string', 'max:255', 'regex:/^[A-Za-z0-9._:-]+$/'],
                'category_id' => ['present', 'nullable', 'string', 'max:100'],
                'amount' => ['required', 'integer'],
                'type' => ['required', Rule::in(['INCOME', 'EXPENSE'])],
                'frequency' => ['required', 'string', 'max:30'],
                'next_due_date' => ['required', 'integer'],
                'is_active' => ['required', 'boolean'],
            ],
        };
    }
}

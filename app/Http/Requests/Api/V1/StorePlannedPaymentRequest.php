<?php

namespace App\Http\Requests\Api\V1;

use App\Models\Category;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Validator;

class StorePlannedPaymentRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        $this->merge(['category_id' => $this->input('category_id', '') ?? '']);
    }

    public function rules(): array
    {
        return [
            'id' => ['required', 'uuid'],
            'name' => ['required', 'string', 'max:120'],
            'account_id' => [
                'required', 'uuid',
                Rule::exists('accounts', 'id')->where('user_id', $this->user()->id),
            ],
            'category_id' => ['present', 'string', 'max:64'],
            'amount' => ['required', 'integer', 'min:1'],
            'type' => ['required', Rule::in(['EXPENSE', 'INCOME'])],
            'frequency' => ['required', Rule::in(['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'YEARLY', 'ONCE'])],
            'next_due_date' => ['required', 'integer', 'min:0'],
            'is_active' => ['required', 'boolean'],
            'is_deleted' => ['required', 'boolean'],
        ];
    }

    public function after(): array
    {
        return [function (Validator $validator): void {
            $categoryId = (string) $this->input('category_id', '');
            if ($categoryId === '') {
                return;
            }
            $exists = Category::query()
                ->where('user_id', $this->user()->id)
                ->where('client_id', $categoryId)
                ->when(! $this->boolean('is_deleted'), fn ($query) => $query->where('is_deleted', false))
                ->exists();
            if (! $exists) {
                $validator->errors()->add('category_id', 'La categoria no existe o pertenece a otro usuario.');
            }
        }];
    }
}

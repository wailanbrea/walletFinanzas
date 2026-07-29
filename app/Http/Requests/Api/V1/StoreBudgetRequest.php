<?php

namespace App\Http\Requests\Api\V1;

use App\Models\Category;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Validator;

class StoreBudgetRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'id' => ['required', 'uuid'],
            'category_id' => ['required', 'string', 'max:64'],
            'limit_amount' => ['required', 'integer', 'min:1'],
            'spent_amount' => ['required', 'integer', 'min:0'],
            'period' => ['required', Rule::in(['WEEKLY', 'MONTHLY', 'YEARLY'])],
            'is_deleted' => ['required', 'boolean'],
        ];
    }

    public function after(): array
    {
        return [function (Validator $validator): void {
            $exists = Category::query()
                ->where('user_id', $this->user()->id)
                ->where('client_id', $this->input('category_id'))
                ->when(! $this->boolean('is_deleted'), fn ($query) => $query->where('is_deleted', false))
                ->exists();
            if (! $exists) {
                $validator->errors()->add('category_id', 'La categoria no existe o pertenece a otro usuario.');
            }
        }];
    }
}

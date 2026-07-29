<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreDebtRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        $this->merge(['description' => $this->input('description') ?? '']);
    }

    public function rules(): array
    {
        return [
            'id' => ['required', 'uuid'],
            'name' => ['required', 'string', 'max:120'],
            'description' => ['present', 'string', 'max:500'],
            'direction' => ['required', Rule::in(['I_OWE', 'OWED_TO_ME'])],
            'total_amount' => ['required', 'integer', 'min:1'],
            'paid_amount' => ['required', 'integer', 'min:0', 'lte:total_amount'],
            'due_date' => ['nullable', 'integer', 'min:0'],
            'is_closed' => ['required', 'boolean'],
            'is_deleted' => ['required', 'boolean'],
        ];
    }
}

<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreAccountRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        // Clientes publicados antes de este campo solo creaban cuentas bancarias.
        $type = strtoupper((string) $this->input('type', 'BANK'));

        $this->merge([
            'type' => $type,
            'credit_limit' => $type === 'CREDIT_CARD' ? $this->input('credit_limit') : null,
            'currency' => strtoupper((string) $this->input('currency', 'DOP')),
            'country_code' => strtoupper((string) $this->input('country_code', 'DO')),
        ]);
    }

    public function rules(): array
    {
        return [
            'id' => ['sometimes', 'string', 'max:255', 'regex:/^[A-Za-z0-9._:-]+$/'],
            'name' => ['required', 'string', 'max:120'],
            'type' => ['required', Rule::in(['CASH', 'BANK', 'SAVINGS', 'DEBIT_CARD', 'CREDIT_CARD'])],
            'credit_limit' => [
                Rule::requiredIf(fn (): bool => $this->input('type') === 'CREDIT_CARD'),
                'nullable',
                'integer',
                'between:1,9000000000000000',
            ],
            'balance' => ['required', 'integer', 'between:-9000000000000000,9000000000000000'],
            'currency' => ['required', 'string', 'size:3', 'regex:/^[A-Z]{3}$/'],
            'institution_name' => ['nullable', 'string', 'max:120'],
            'country_code' => ['required', 'string', 'size:2', 'regex:/^[A-Z]{2}$/'],
            'card_last_four' => ['nullable', 'regex:/^[0-9]{4}$/'],
            'is_active' => ['sometimes', 'boolean'],
        ];
    }
}

<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Foundation\Http\FormRequest;

class StoreAccountRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        $this->merge([
            'currency' => strtoupper((string) $this->input('currency', 'DOP')),
            'country_code' => strtoupper((string) $this->input('country_code', 'DO')),
        ]);
    }

    public function rules(): array
    {
        return [
            'id' => ['sometimes', 'string', 'max:255', 'regex:/^[A-Za-z0-9._:-]+$/'],
            'name' => ['required', 'string', 'max:120'],
            'balance' => ['required', 'integer', 'between:-9000000000000000,9000000000000000'],
            'currency' => ['required', 'string', 'size:3', 'regex:/^[A-Z]{3}$/'],
            'institution_name' => ['nullable', 'string', 'max:120'],
            'country_code' => ['required', 'string', 'size:2', 'regex:/^[A-Z]{2}$/'],
            'card_last_four' => ['nullable', 'regex:/^[0-9]{4}$/'],
            'is_active' => ['sometimes', 'boolean'],
        ];
    }
}

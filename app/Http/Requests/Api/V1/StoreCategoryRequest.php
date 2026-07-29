<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Foundation\Http\FormRequest;

class StoreCategoryRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        $this->merge([
            'id' => trim((string) $this->input('id')),
            'name' => trim((string) $this->input('name')),
            'icon' => trim((string) $this->input('icon')),
            'color_hex' => strtoupper(trim((string) $this->input('color_hex'))),
        ]);
    }

    public function rules(): array
    {
        return [
            'id' => ['required', 'string', 'max:64', 'regex:/^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/'],
            'name' => ['required', 'string', 'max:80'],
            'icon' => ['required', 'string', 'max:50'],
            'color_hex' => ['required', 'regex:/^#[0-9A-F]{6}$/'],
            'is_deleted' => ['required', 'boolean'],
        ];
    }
}

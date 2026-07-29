<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Foundation\Http\FormRequest;

class StoreGoalRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    public function rules(): array
    {
        return [
            'id' => ['required', 'uuid'],
            'name' => ['required', 'string', 'max:120'],
            'icon' => ['required', 'string', 'max:50'],
            'target_amount' => ['required', 'integer', 'min:1'],
            'saved_amount' => ['required', 'integer', 'min:0', 'lte:target_amount'],
            'target_date' => ['nullable', 'integer', 'min:0'],
            'is_completed' => ['required', 'boolean'],
            'is_deleted' => ['required', 'boolean'],
        ];
    }
}

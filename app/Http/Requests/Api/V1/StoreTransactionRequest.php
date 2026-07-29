<?php

namespace App\Http\Requests\Api\V1;

use Carbon\CarbonImmutable;
use Carbon\Exceptions\InvalidFormatException;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreTransactionRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    protected function prepareForValidation(): void
    {
        $amount = $this->input('amount');

        if (is_string($amount) && preg_match('/^[+-]?\d+$/D', $amount) === 1) {
            $amount = (int) $amount;
        }

        $timestamp = $this->input('timestamp');

        if (is_string($timestamp)) {
            try {
                $timestamp = CarbonImmutable::parse($timestamp)
                    ->utc()
                    ->startOfSecond()
                    ->format('Y-m-d\TH:i:s\Z');
            } catch (InvalidFormatException) {
                // Preserve invalid input so the validation rule returns 422.
            }
        }

        $this->merge([
            'amount' => $amount,
            'currency' => strtoupper((string) $this->input('currency')),
            'timestamp' => $timestamp,
        ]);
    }

    public function rules(): array
    {
        return [
            'account_id' => ['required', 'uuid'],
            'idempotency_key' => ['required', 'uuid'],
            'amount' => ['required', 'integer', 'not_in:0', 'between:-9000000000000000,9000000000000000'],
            'currency' => ['required', 'string', 'size:3', 'regex:/^[A-Z]{3}$/'],
            'description' => ['nullable', 'string', 'max:500'],
            'category_id' => ['nullable', 'string', 'max:64'],
            'timestamp' => ['required', 'date'],
            'status' => ['required', Rule::in(['pending', 'completed', 'cancelled'])],
        ];
    }
}

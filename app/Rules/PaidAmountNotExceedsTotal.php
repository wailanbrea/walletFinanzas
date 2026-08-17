<?php

namespace App\Rules;

use App\Models\WalletSyncResource;
use Illuminate\Contracts\Validation\Rule;

class PaidAmountNotExceedsTotal implements Rule
{
    protected array $request;

    public function __construct(
        protected WalletSyncResource $debt,
        array $request
    ) {
        $this->request = $request;
    }

    protected function getArrayValue(array $data, string $key, $default = null)
    {
        foreach (explode('.', $key) as $segment) {
            if (is_array($data) && array_key_exists($segment, $data)) {
                $data = $data[$segment];
            } else {
                return $default;
            }
        }

        return $data;
    }

    public function passes($attribute, $value): bool
    {
        if ($value === null || $value === '') {
            return true;
        }

        // Buscar total_amount: primero en el request, luego fallback al DB
        $totalAmount = $this->getArrayValue($this->request, 'total_amount');
        if ($totalAmount === null) {
            $totalAmount = $this->debt->total_amount;
        }

        if ($totalAmount === null || $totalAmount === '') {
            return true;
        }

        return (int) $value <= (int) $totalAmount;
    }

    public function message()
    {
        return 'El monto pagado no puede ser mayor que el monto total.';
    }
}

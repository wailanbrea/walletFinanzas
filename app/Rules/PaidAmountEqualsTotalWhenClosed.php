<?php

namespace App\Rules;

use App\Models\WalletSyncResource;
use Illuminate\Contracts\Validation\Rule;

class PaidAmountEqualsTotalWhenClosed implements Rule
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
        // $value es el valor de is_closed que se está validando
        if ($value !== true) {
            return true;
        }

        $paidAmount = $this->getArrayValue($this->request, 'paid_amount');
        $totalAmount = $this->getArrayValue($this->request, 'total_amount', $this->debt->total_amount);

        // Si paid_amount no viene en el request, no se puede cerrar la deuda
        if ($paidAmount === null || $paidAmount === '') {
            return false;
        }

        // Si total_amount no viene en el request, usar el valor actual del registro
        if ($totalAmount === null || $totalAmount === '') {
            return false;
        }

        return (int) $paidAmount === (int) $totalAmount;
    }

    public function message()
    {
        return 'El monto pagado debe ser igual al monto total cuando la deuda está cerrada.';
    }
}

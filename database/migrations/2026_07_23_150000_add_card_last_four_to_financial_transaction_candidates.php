<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Guarda los ultimos cuatro digitos de la tarjeta que origino el movimiento para que
 * la app preseleccione la cuenta correcta al aceptar el correo.
 *
 * Solo cuatro digitos, nunca el numero completo: es lo minimo para emparejar contra
 * las cuentas del usuario y no tiene valor para nadie que acceda a la base.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('financial_transaction_candidates', function (Blueprint $table): void {
            $table->char('card_last_four', 4)->nullable()->after('merchant');
        });
    }

    public function down(): void
    {
        Schema::table('financial_transaction_candidates', function (Blueprint $table): void {
            $table->dropColumn('card_last_four');
        });
    }
};

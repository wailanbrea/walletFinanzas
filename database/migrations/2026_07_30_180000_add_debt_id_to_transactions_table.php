<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Un movimiento puede pertenecer a una deuda: el gasto que la origino o un abono.
 *
 * Sin esto el vinculo se queda en el telefono donde se creo, y el otro dispositivo ve
 * el movimiento y la deuda por separado: lo prestado volveria a contarse como gasto
 * propio alli.
 *
 * Es texto y no clave foranea a proposito: las deudas no tienen tabla, viajan como
 * recursos de sincronizacion, asi que aqui se guarda el id que les da el cliente.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('transactions', function (Blueprint $table) {
            $table->string('debt_id', 100)->nullable()->after('category_id');
            $table->index(['user_id', 'debt_id']);
        });
    }

    public function down(): void
    {
        Schema::table('transactions', function (Blueprint $table) {
            $table->dropIndex(['user_id', 'debt_id']);
            $table->dropColumn('debt_id');
        });
    }
};

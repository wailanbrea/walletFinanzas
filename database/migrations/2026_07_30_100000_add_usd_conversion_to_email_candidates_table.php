<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Instantanea de la conversion a la divisa base del usuario.
 *
 * Un cargo en USD no se podia registrar en una cuenta en DOP: la app solo acepta un
 * candidato cuya divisa coincida con la de la cuenta, o que traiga una conversion.
 *
 * La tasa se guarda junto al movimiento, no se recalcula al mostrarlo: un gasto del
 * mes pasado debe seguir valiendo lo que valia ese dia. Se almacena en millonesimas
 * (micros) para no arrastrar errores de coma flotante, y con su fecha y origen para
 * que la cifra sea auditable.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('email_candidates', function (Blueprint $table): void {
            $table->bigInteger('converted_amount')->nullable()->after('currency');
            $table->char('converted_currency', 3)->nullable()->after('converted_amount');
            $table->unsignedBigInteger('exchange_rate_micros')->nullable()->after('converted_currency');
            $table->timestamp('exchange_rate_at')->nullable()->after('exchange_rate_micros');
            $table->string('exchange_rate_source', 64)->nullable()->after('exchange_rate_at');
        });
    }

    public function down(): void
    {
        Schema::table('email_candidates', function (Blueprint $table): void {
            $table->dropColumn([
                'converted_amount',
                'converted_currency',
                'exchange_rate_micros',
                'exchange_rate_at',
                'exchange_rate_source',
            ]);
        });
    }
};

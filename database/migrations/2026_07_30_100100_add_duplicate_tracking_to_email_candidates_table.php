<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Un mismo cargo llega por dos buzones: PayPal lo avisa en USD y el banco emisor lo
 * avisa en DOP. El dedupe existente es por mensaje e incluye el proveedor, asi que no
 * puede verlos como uno solo y el gasto aparece dos veces.
 *
 * Se apunta a cual candidato se considera el bueno en vez de borrar el repetido: asi
 * el usuario puede ver por que se oculto y deshacerlo si el emparejamiento fue erroneo.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('email_candidates', function (Blueprint $table): void {
            $table->foreignUuid('duplicate_of_id')
                ->nullable()
                ->after('status')
                ->constrained('email_candidates')
                ->nullOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('email_candidates', function (Blueprint $table): void {
            $table->dropConstrainedForeignId('duplicate_of_id');
        });
    }
};

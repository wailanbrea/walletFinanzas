<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Las categorias sincronizadas guardan si son de gasto, de ingreso o de ambos.
 *
 * Sin esta columna el tipo nunca salia del telefono: al bajar, cada categoria se
 * reconstruia con el valor por defecto, asi que "Salario" volvia a ser de gasto y al
 * registrar un ingreso no habia ninguna categoria que ofrecer.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('categories', function (Blueprint $table) {
            $table->string('type', 10)->default('EXPENSE')->after('color_hex');
        });
    }

    public function down(): void
    {
        Schema::table('categories', function (Blueprint $table) {
            $table->dropColumn('type');
        });
    }
};

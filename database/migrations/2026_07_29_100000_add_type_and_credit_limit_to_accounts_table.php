<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Hasta esta migracion el cliente interpretaba toda cuenta remota como BANK, de modo
 * que una tarjeta de credito sincronizada perdia su tipo y su limite: al bajar a otro
 * telefono aparecia como cuenta de banco y su saldo sumaba al Balance Total en vez de
 * contar como deuda.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            $table->string('type', 20)->default('BANK')->after('name');
            $table->bigInteger('credit_limit')->nullable()->after('balance');
        });
    }

    public function down(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            $table->dropColumn(['type', 'credit_limit']);
        });
    }
};

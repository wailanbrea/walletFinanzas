<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Índices para el pull incremental (?updated_since=) del cliente offline-first:
 * las listas de cuentas y movimientos filtran/ordenan por updated_at.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            $table->index(['user_id', 'updated_at']);
        });

        Schema::table('transactions', function (Blueprint $table): void {
            $table->index(['user_id', 'updated_at']);
            $table->index(['account_id', 'updated_at']);
        });
    }

    public function down(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            $table->dropIndex(['user_id', 'updated_at']);
        });

        Schema::table('transactions', function (Blueprint $table): void {
            $table->dropIndex(['user_id', 'updated_at']);
            $table->dropIndex(['account_id', 'updated_at']);
        });
    }
};

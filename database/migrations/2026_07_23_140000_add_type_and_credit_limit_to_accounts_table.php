<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            // Hasta esta migracion el cliente interpretaba toda cuenta remota como BANK.
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

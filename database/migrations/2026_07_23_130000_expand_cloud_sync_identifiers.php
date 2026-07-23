<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('transactions', function (Blueprint $table): void {
            $table->dropForeign(['account_id']);
            $table->dropUnique(['user_id', 'idempotency_key']);
        });
        Schema::table('accounts', function (Blueprint $table): void {
            $table->string('id', 255)->change();
        });
        Schema::table('transactions', function (Blueprint $table): void {
            $table->string('account_id', 255)->change();
            $table->string('idempotency_key', 255)->nullable()->change();
            $table->string('category_id', 100)->nullable()->change();
            $table->unique(['user_id', 'idempotency_key']);
            $table->foreign('account_id')->references('id')->on('accounts')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::table('transactions', function (Blueprint $table): void {
            $table->dropForeign(['account_id']);
            $table->dropUnique(['user_id', 'idempotency_key']);
        });
        Schema::table('accounts', function (Blueprint $table): void {
            $table->uuid('id')->change();
        });
        Schema::table('transactions', function (Blueprint $table): void {
            $table->uuid('account_id')->change();
            $table->uuid('idempotency_key')->nullable()->change();
            $table->uuid('category_id')->nullable()->change();
            $table->unique(['user_id', 'idempotency_key']);
            $table->foreign('account_id')->references('id')->on('accounts')->cascadeOnDelete();
        });
    }
};

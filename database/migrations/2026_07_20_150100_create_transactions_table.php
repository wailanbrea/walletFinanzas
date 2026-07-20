<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('transactions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignUuid('account_id')->constrained()->cascadeOnDelete();
            $table->bigInteger('amount');
            $table->char('currency', 3);
            $table->string('description', 500)->nullable();
            $table->uuid('category_id')->nullable();
            $table->timestamp('occurred_at');
            $table->string('status', 20)->default('completed');
            $table->timestamps();

            $table->index(['user_id', 'occurred_at']);
            $table->index(['account_id', 'occurred_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('transactions');
    }
};

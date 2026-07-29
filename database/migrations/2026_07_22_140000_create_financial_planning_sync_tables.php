<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('budgets', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->uuid('client_id');
            $table->string('category_id', 64);
            $table->unsignedBigInteger('limit_amount');
            $table->unsignedBigInteger('spent_amount')->default(0);
            $table->string('period', 20)->default('MONTHLY');
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->unique(['user_id', 'client_id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('goals', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->uuid('client_id');
            $table->string('name', 120);
            $table->string('icon', 50)->default('track_changes');
            $table->unsignedBigInteger('target_amount');
            $table->unsignedBigInteger('saved_amount')->default(0);
            $table->unsignedBigInteger('target_date')->nullable();
            $table->boolean('is_completed')->default(false);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->unique(['user_id', 'client_id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('debts', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->uuid('client_id');
            $table->string('name', 120);
            $table->string('description', 500)->default('');
            $table->string('direction', 20);
            $table->unsignedBigInteger('total_amount');
            $table->unsignedBigInteger('paid_amount')->default(0);
            $table->unsignedBigInteger('due_date')->nullable();
            $table->boolean('is_closed')->default(false);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->unique(['user_id', 'client_id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('planned_payments', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->uuid('client_id');
            $table->foreignUuid('account_id')->constrained()->cascadeOnDelete();
            $table->string('category_id', 64)->default('');
            $table->string('name', 120);
            $table->unsignedBigInteger('amount');
            $table->string('type', 20);
            $table->string('frequency', 20);
            $table->unsignedBigInteger('next_due_date');
            $table->boolean('is_active')->default(true);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->unique(['user_id', 'client_id']);
            $table->index(['user_id', 'updated_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('planned_payments');
        Schema::dropIfExists('debts');
        Schema::dropIfExists('goals');
        Schema::dropIfExists('budgets');
    }
};

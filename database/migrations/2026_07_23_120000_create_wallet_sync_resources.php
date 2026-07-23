<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('categories', function (Blueprint $table): void {
            $table->string('id', 100);
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name', 120);
            $table->string('icon', 80);
            $table->char('color_hex', 7);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->primary(['user_id', 'id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('budgets', function (Blueprint $table): void {
            $table->string('id', 100);
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('category_id', 100);
            $table->bigInteger('limit_amount');
            $table->bigInteger('spent_amount')->default(0);
            $table->string('period', 30);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->primary(['user_id', 'id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('goals', function (Blueprint $table): void {
            $table->string('id', 100);
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name', 120);
            $table->string('icon', 80);
            $table->bigInteger('target_amount');
            $table->bigInteger('saved_amount')->default(0);
            $table->bigInteger('target_date')->nullable();
            $table->boolean('is_completed')->default(false);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->primary(['user_id', 'id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('debts', function (Blueprint $table): void {
            $table->string('id', 100);
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name', 120);
            $table->text('description')->nullable();
            $table->string('direction', 20);
            $table->bigInteger('total_amount');
            $table->bigInteger('paid_amount')->default(0);
            $table->bigInteger('due_date')->nullable();
            $table->boolean('is_closed')->default(false);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->primary(['user_id', 'id']);
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('planned_payments', function (Blueprint $table): void {
            $table->string('id', 100);
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('account_id', 255);
            $table->string('category_id', 100)->default('');
            $table->string('name', 120);
            $table->bigInteger('amount');
            $table->string('type', 20);
            $table->string('frequency', 30);
            $table->bigInteger('next_due_date');
            $table->boolean('is_active')->default(true);
            $table->boolean('is_deleted')->default(false);
            $table->timestamps();
            $table->primary(['user_id', 'id']);
            $table->index(['user_id', 'updated_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('planned_payments');
        Schema::dropIfExists('debts');
        Schema::dropIfExists('goals');
        Schema::dropIfExists('budgets');
        Schema::dropIfExists('categories');
    }
};

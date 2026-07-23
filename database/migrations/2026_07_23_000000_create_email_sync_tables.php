<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('email_connections', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('provider', 20);
            $table->string('email');
            $table->string('status', 20)->default('connected');
            $table->text('access_token');
            $table->text('refresh_token')->nullable();
            $table->dateTime('token_expires_at')->nullable();
            $table->dateTime('connected_at');
            $table->dateTime('last_synced_at')->nullable();
            $table->timestamps();
            $table->unique(['user_id', 'provider']);
        });

        Schema::create('email_oauth_states', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('provider', 20);
            $table->char('state_hash', 64)->unique();
            $table->text('code_verifier');
            $table->dateTime('expires_at');
            $table->dateTime('used_at')->nullable();
            $table->timestamps();
            $table->index(['provider', 'expires_at']);
        });

        Schema::create('email_sync_runs', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignUuid('email_connection_id')->constrained()->cascadeOnDelete();
            $table->string('provider', 20);
            $table->string('status', 20)->default('queued');
            $table->unsignedInteger('messages_discovered')->default(0);
            $table->unsignedInteger('messages_created')->default(0);
            $table->unsignedInteger('candidates_created')->default(0);
            $table->unsignedInteger('conversions_backfilled')->default(0);
            $table->string('error_code', 60)->nullable();
            $table->dateTime('started_at')->nullable();
            $table->dateTime('finished_at')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'provider', 'created_at']);
        });

        Schema::create('provider_messages', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignUuid('email_connection_id')->constrained()->cascadeOnDelete();
            $table->string('provider', 20);
            $table->string('provider_message_id', 255);
            $table->string('subject', 500)->nullable();
            $table->text('snippet')->nullable();
            $table->dateTime('occurred_at')->nullable();
            $table->timestamps();
            $table->unique(['user_id', 'provider', 'provider_message_id'], 'provider_messages_dedupe');
        });

        Schema::create('email_candidates', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('provider_message_id')->constrained('provider_messages')->cascadeOnDelete();
            $table->string('provider', 20);
            $table->string('merchant', 255)->nullable();
            $table->bigInteger('amount');
            $table->char('currency', 3);
            $table->string('direction', 20);
            $table->string('category_suggestion', 120)->nullable();
            $table->dateTime('occurred_at');
            $table->unsignedTinyInteger('confidence');
            $table->string('status', 20)->default('pending');
            $table->string('subject', 500)->nullable();
            $table->string('category', 120)->nullable();
            $table->timestamps();
            $table->unique('provider_message_id');
            $table->index(['user_id', 'status', 'occurred_at']);
        });

        Schema::create('email_categorization_rules', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('merchant', 255);
            $table->string('category', 120);
            $table->timestamps();
            $table->unique(['user_id', 'merchant']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('email_categorization_rules');
        Schema::dropIfExists('email_candidates');
        Schema::dropIfExists('provider_messages');
        Schema::dropIfExists('email_sync_runs');
        Schema::dropIfExists('email_oauth_states');
        Schema::dropIfExists('email_connections');
    }
};

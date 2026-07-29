<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('email_connections', function (Blueprint $table): void {
            $table->timestamp('last_sync_at')->nullable()->after('expires_at');
            $table->string('last_sync_status', 32)->nullable()->after('last_sync_at');
        });

        Schema::create('email_sync_runs', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('email_connection_id')->constrained()->cascadeOnDelete();
            $table->string('status', 32);
            $table->unsignedInteger('messages_discovered')->default(0);
            $table->unsignedInteger('messages_created')->default(0);
            $table->unsignedInteger('candidates_created')->default(0);
            $table->string('error_code', 64)->nullable();
            $table->timestamp('started_at');
            $table->timestamp('finished_at')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'started_at']);
        });

        Schema::create('email_messages', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('email_connection_id')->constrained()->cascadeOnDelete();
            $table->string('provider_message_id', 191);
            $table->string('internet_message_id', 191)->nullable();
            $table->string('sender_email', 320)->nullable();
            $table->string('sender_name')->nullable();
            $table->string('subject', 512)->nullable();
            $table->timestamp('received_at');
            $table->text('body_excerpt')->nullable();
            $table->char('content_hash', 64);
            $table->string('status', 32)->default('processed');
            $table->timestamps();
            $table->unique(['email_connection_id', 'provider_message_id']);
            $table->index(['user_id', 'received_at']);
        });

        Schema::create('financial_transaction_candidates', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('email_message_id')->unique()->constrained()->cascadeOnDelete();
            $table->string('provider', 32);
            $table->string('merchant')->nullable();
            $table->bigInteger('amount');
            $table->char('currency', 3);
            $table->string('direction', 16);
            $table->string('category_suggestion')->nullable();
            $table->timestamp('occurred_at');
            $table->unsignedTinyInteger('confidence');
            $table->json('reasons');
            $table->string('status', 32)->default('pending');
            $table->timestamps();
            $table->index(['user_id', 'status', 'occurred_at'], 'email_candidates_user_status_date_idx');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('financial_transaction_candidates');
        Schema::dropIfExists('email_messages');
        Schema::dropIfExists('email_sync_runs');

        Schema::table('email_connections', function (Blueprint $table): void {
            $table->dropColumn(['last_sync_at', 'last_sync_status']);
        });
    }
};

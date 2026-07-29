<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('email_classification_rules', function (Blueprint $table): void {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('provider', 32);
            $table->string('type', 32);
            $table->char('sender_hash', 64);
            $table->char('subject_fingerprint', 64)->default('');
            $table->string('category', 100)->nullable();
            $table->timestamps();
            $table->unique(
                ['user_id', 'provider', 'type', 'sender_hash', 'subject_fingerprint'],
                'email_rules_user_provider_type_sender_subject_unique'
            );
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('email_classification_rules');
    }
};

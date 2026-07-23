<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('bank_connections', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('provider_name', 120);
            $table->string('provider_code', 120);
            $table->char('country_code', 2);
            $table->string('status', 30)->default('pending');
            $table->timestamp('last_sync_at')->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'provider_code']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('bank_connections');
    }
};

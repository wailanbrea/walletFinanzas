<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('provider_messages', function (Blueprint $table): void {
            $table->string('sender_name')->nullable()->after('snippet');
            $table->string('sender_address', 320)->nullable()->after('sender_name');
            $table->string('sender_domain')->nullable()->after('sender_address');
            $table->index(['user_id', 'sender_domain'], 'provider_messages_sender_lookup');
        });
    }

    public function down(): void
    {
        Schema::table('provider_messages', function (Blueprint $table): void {
            $table->dropIndex('provider_messages_sender_lookup');
            $table->dropColumn(['sender_name', 'sender_address', 'sender_domain']);
        });
    }
};

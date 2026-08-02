<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('email_candidates', function (Blueprint $table): void {
            // Nullable on purpose: historical pending candidates are never guessed or mass-updated.
            $table->string('event_type', 50)->nullable()->after('direction');
            $table->index(['user_id', 'status', 'event_type'], 'email_candidates_event_lookup');
        });
    }

    public function down(): void
    {
        Schema::table('email_candidates', function (Blueprint $table): void {
            $table->dropIndex('email_candidates_event_lookup');
            $table->dropColumn('event_type');
        });
    }
};

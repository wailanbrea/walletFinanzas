<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        if (! Schema::hasTable('email_connections')) {
            return;
        }

        DB::statement("ALTER TABLE `email_connections` MODIFY `status` VARCHAR(40) NOT NULL DEFAULT 'connected'");
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        if (! Schema::hasTable('email_connections')) {
            return;
        }

        DB::statement("ALTER TABLE `email_connections` MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'connected'");
    }
};

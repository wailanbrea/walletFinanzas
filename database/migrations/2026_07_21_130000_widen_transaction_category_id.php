<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * El cliente usa ids de categoría legibles ("cat_alimentacion"), no UUID. Ampliamos
 * la columna a string para el sync app→backend.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('transactions', function (Blueprint $table): void {
            $table->string('category_id', 64)->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('transactions', function (Blueprint $table): void {
            $table->uuid('category_id')->nullable()->change();
        });
    }
};

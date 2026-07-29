<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('financial_transaction_candidates', function (Blueprint $table): void {
            $table->bigInteger('converted_amount')->nullable()->after('currency');
            $table->char('converted_currency', 3)->nullable()->after('converted_amount');
            $table->unsignedBigInteger('exchange_rate_micros')->nullable()->after('converted_currency');
            $table->timestamp('exchange_rate_at')->nullable()->after('exchange_rate_micros');
            $table->string('exchange_rate_source', 64)->nullable()->after('exchange_rate_at');
        });
    }

    public function down(): void
    {
        Schema::table('financial_transaction_candidates', function (Blueprint $table): void {
            $table->dropColumn([
                'converted_amount',
                'converted_currency',
                'exchange_rate_micros',
                'exchange_rate_at',
                'exchange_rate_source',
            ]);
        });
    }
};

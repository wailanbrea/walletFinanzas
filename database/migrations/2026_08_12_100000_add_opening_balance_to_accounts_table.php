<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            $table->bigInteger('opening_balance')->default(0)->after('balance');
        });

        // Freeze the current residual (balance - sum of completed transactions)
        // as the baseline opening_balance so any future divergence is a real
        // discrepancy.
        DB::table('accounts')->get()->each(function (object $account): void {
            $completedSum = (int) DB::table('transactions')
                ->where('account_id', $account->id)
                ->where('status', 'completed')
                ->sum('amount');

            DB::table('accounts')
                ->where('id', $account->id)
                ->update(['opening_balance' => $account->balance - $completedSum]);
        });
    }

    public function down(): void
    {
        Schema::table('accounts', function (Blueprint $table): void {
            $table->dropColumn('opening_balance');
        });
    }
};

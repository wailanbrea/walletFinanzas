<?php

use App\Http\Controllers\Api\V1\AccountController;
use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\BankConnectionController;
use App\Http\Controllers\Api\V1\EmailCandidateController;
use App\Http\Controllers\Api\V1\EmailConnectionController;
use App\Http\Controllers\Api\V1\EmailOAuthCallbackController;
use App\Http\Controllers\Api\V1\TransactionController;
use App\Http\Controllers\Api\V1\WalletSyncResourceController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function (): void {
    Route::get('/health', fn () => response()->json([
        'status' => 'ok',
        'service' => 'wallet-finanzas-api',
        'version' => 'v1',
    ]));

    Route::prefix('auth')->middleware('throttle:10,1')->group(function (): void {
        Route::post('/register', [AuthController::class, 'register']);
        Route::post('/login', [AuthController::class, 'login']);
    });

    Route::get('/oauth/{provider}/callback', EmailOAuthCallbackController::class)
        ->whereIn('provider', ['gmail', 'microsoft'])
        ->middleware('throttle:30,1');

    Route::middleware('auth:sanctum')->group(function (): void {
        Route::post('/auth/logout', [AuthController::class, 'logout'])->middleware('wallet-abilities:wallet.read');
        Route::get('/user', [AuthController::class, 'profile'])->middleware('wallet-abilities:wallet.read');
        Route::patch('/user', [AuthController::class, 'updateProfile'])->middleware('wallet-abilities:wallet.write');
        Route::get('/accounts', [AccountController::class, 'index'])->middleware('wallet-abilities:wallet.read');
        Route::post('/accounts', [AccountController::class, 'store'])->middleware('wallet-abilities:wallet.write');
        Route::get('/transactions', [TransactionController::class, 'index'])->middleware('wallet-abilities:wallet.read');
        Route::post('/transactions', [TransactionController::class, 'store'])->middleware('wallet-abilities:wallet.write');
        Route::patch('/transactions/{transaction}', [TransactionController::class, 'update'])->middleware('wallet-abilities:wallet.write');
        Route::delete('/transactions/{transaction}', [TransactionController::class, 'destroy'])->middleware('wallet-abilities:wallet.delete');
        Route::get('/categories', [WalletSyncResourceController::class, 'categories'])->middleware('wallet-abilities:wallet.read');
        Route::post('/categories', [WalletSyncResourceController::class, 'storeCategory'])->middleware('wallet-abilities:wallet.write');
        Route::get('/budgets', [WalletSyncResourceController::class, 'budgets'])->middleware('wallet-abilities:wallet.read');
        Route::post('/budgets', [WalletSyncResourceController::class, 'storeBudget'])->middleware('wallet-abilities:wallet.write');
        Route::get('/goals', [WalletSyncResourceController::class, 'goals'])->middleware('wallet-abilities:wallet.read');
        Route::post('/goals', [WalletSyncResourceController::class, 'storeGoal'])->middleware('wallet-abilities:wallet.write');
        Route::get('/debts', [WalletSyncResourceController::class, 'debts'])->middleware('wallet-abilities:wallet.read');
        Route::post('/debts', [WalletSyncResourceController::class, 'storeDebt'])->middleware('wallet-abilities:wallet.write');
        Route::patch('/debts/{debt}', [WalletSyncResourceController::class, 'updateDebt'])->middleware('wallet-abilities:wallet.write');
        Route::get('/planned-payments', [WalletSyncResourceController::class, 'plannedPayments'])->middleware('wallet-abilities:wallet.read');
        Route::post('/planned-payments', [WalletSyncResourceController::class, 'storePlannedPayment'])->middleware('wallet-abilities:wallet.write');
        Route::get('/bank-connections', [BankConnectionController::class, 'index'])->middleware('wallet-abilities:wallet.read');
        Route::get('/email-connections', [EmailConnectionController::class, 'index'])->middleware('wallet-abilities:wallet.read');
        Route::post('/email-connections/{provider}/authorization-url', [EmailConnectionController::class, 'authorizationUrl'])->middleware('wallet-abilities:wallet.write');
        Route::post('/email-connections/{provider}/sync', [EmailConnectionController::class, 'sync'])->middleware('wallet-abilities:wallet.write');
        Route::get('/email-connections/{provider}/sync-runs/{run}', [EmailConnectionController::class, 'syncRun'])->middleware('wallet-abilities:wallet.read');
        Route::delete('/email-connections/{provider}', [EmailConnectionController::class, 'destroy'])->middleware('wallet-abilities:wallet.delete');
        Route::get('/email-candidates', [EmailCandidateController::class, 'index'])->middleware('wallet-abilities:wallet.read');
        Route::patch('/email-candidates/{candidate}', [EmailCandidateController::class, 'update'])->middleware('wallet-abilities:wallet.write');

        // Hermes receives only its own abilities; it must not use the app routes.
        Route::prefix('agent')->group(function (): void {
            Route::get('/accounts', [AccountController::class, 'index'])->middleware('abilities:agent.read');
            Route::get('/transactions', [TransactionController::class, 'index'])->middleware('abilities:agent.read');
            Route::get('/debts', [WalletSyncResourceController::class, 'debts'])->middleware('abilities:agent.read');
            Route::post('/accounts', [AccountController::class, 'store'])->middleware('abilities:agent.write');
            Route::post('/transactions', [TransactionController::class, 'store'])->middleware('abilities:agent.write');
            Route::patch('/transactions/{transaction}', [TransactionController::class, 'update'])->middleware('abilities:agent.write');
            Route::post('/debts', [WalletSyncResourceController::class, 'storeDebt'])->middleware('abilities:agent.write');
            Route::patch('/debts/{debt}', [WalletSyncResourceController::class, 'updateDebt'])->middleware('abilities:agent.write');
            Route::delete('/transactions/{transaction}', [TransactionController::class, 'destroy'])->middleware('abilities:agent.delete');
        });
    });
});

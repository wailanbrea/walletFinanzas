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
        Route::post('/auth/logout', [AuthController::class, 'logout']);
        Route::get('/user', [AuthController::class, 'profile']);
        Route::patch('/user', [AuthController::class, 'updateProfile']);
        Route::get('/accounts', [AccountController::class, 'index']);
        Route::post('/accounts', [AccountController::class, 'store']);
        Route::get('/transactions', [TransactionController::class, 'index']);
        Route::post('/transactions', [TransactionController::class, 'store']);
        Route::patch('/transactions/{transaction}', [TransactionController::class, 'update']);
        Route::delete('/transactions/{transaction}', [TransactionController::class, 'destroy']);
        Route::get('/categories', [WalletSyncResourceController::class, 'categories']);
        Route::post('/categories', [WalletSyncResourceController::class, 'storeCategory']);
        Route::get('/budgets', [WalletSyncResourceController::class, 'budgets']);
        Route::post('/budgets', [WalletSyncResourceController::class, 'storeBudget']);
        Route::get('/goals', [WalletSyncResourceController::class, 'goals']);
        Route::post('/goals', [WalletSyncResourceController::class, 'storeGoal']);
        Route::get('/debts', [WalletSyncResourceController::class, 'debts']);
        Route::post('/debts', [WalletSyncResourceController::class, 'storeDebt']);
        Route::get('/planned-payments', [WalletSyncResourceController::class, 'plannedPayments']);
        Route::post('/planned-payments', [WalletSyncResourceController::class, 'storePlannedPayment']);
        Route::get('/bank-connections', [BankConnectionController::class, 'index']);
        Route::get('/email-connections', [EmailConnectionController::class, 'index']);
        Route::post('/email-connections/{provider}/authorization-url', [EmailConnectionController::class, 'authorizationUrl']);
        Route::post('/email-connections/{provider}/sync', [EmailConnectionController::class, 'sync']);
        Route::get('/email-connections/{provider}/sync-runs/{run}', [EmailConnectionController::class, 'syncRun']);
        Route::delete('/email-connections/{provider}', [EmailConnectionController::class, 'destroy']);
        Route::get('/email-candidates', [EmailCandidateController::class, 'index']);
        Route::patch('/email-candidates/{candidate}', [EmailCandidateController::class, 'update']);
    });
});

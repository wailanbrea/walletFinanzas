<?php

use App\Http\Controllers\Api\V1\AccountController;
use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\BankConnectionController;
use App\Http\Controllers\Api\V1\BudgetController;
use App\Http\Controllers\Api\V1\CategoryController;
use App\Http\Controllers\Api\V1\DebtController;
use App\Http\Controllers\Api\V1\EmailCandidateController;
use App\Http\Controllers\Api\V1\EmailConnectionController;
use App\Http\Controllers\Api\V1\GoalController;
use App\Http\Controllers\Api\V1\PlannedPaymentController;
use App\Http\Controllers\Api\V1\TransactionController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function (): void {
    Route::get('/health', fn () => response()->json([
        'status' => 'ok',
        'service' => 'wallet-finanzas-api',
        'version' => 'v1',
    ]));

    Route::prefix('auth')->group(function (): void {
        // Límites específicos por acción (ver AppServiceProvider): login por email+IP,
        // recuperación de contraseña más estricta, registro por IP.
        Route::post('/register', [AuthController::class, 'register'])->middleware('throttle:register');
        Route::post('/login', [AuthController::class, 'login'])->middleware('throttle:login');
        Route::post('/forgot-password', [AuthController::class, 'forgotPassword'])->middleware('throttle:forgot-password');
    });

    Route::middleware(['auth:sanctum', 'abilities:wallet'])->group(function (): void {
        Route::post('/auth/logout', [AuthController::class, 'logout']);
        Route::get('/accounts', [AccountController::class, 'index']);
        Route::post('/accounts', [AccountController::class, 'store']);
        Route::get('/categories', [CategoryController::class, 'index']);
        Route::post('/categories', [CategoryController::class, 'store']);
        Route::get('/budgets', [BudgetController::class, 'index']);
        Route::post('/budgets', [BudgetController::class, 'store']);
        Route::get('/goals', [GoalController::class, 'index']);
        Route::post('/goals', [GoalController::class, 'store']);
        Route::get('/debts', [DebtController::class, 'index']);
        Route::post('/debts', [DebtController::class, 'store']);
        Route::get('/planned-payments', [PlannedPaymentController::class, 'index']);
        Route::post('/planned-payments', [PlannedPaymentController::class, 'store']);
        Route::get('/transactions', [TransactionController::class, 'index']);
        Route::post('/transactions', [TransactionController::class, 'store']);
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

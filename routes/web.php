<?php

use App\Http\Controllers\AdminController;
use App\Http\Controllers\EmailOAuthCallbackController;
use App\Http\Controllers\PasswordResetController;
use App\Http\Controllers\WebAuthController;
use Illuminate\Support\Facades\Route;

Route::get('/', fn () => redirect(auth()->check() ? '/dashboard' : '/login'));

Route::middleware('guest')->group(function (): void {
    Route::get('/login', [WebAuthController::class, 'create'])->name('login');
    Route::post('/login', [WebAuthController::class, 'store'])->middleware('throttle:10,1');
    Route::get('/reset-password/{token}', [PasswordResetController::class, 'create'])
        ->name('password.reset');
    Route::post('/reset-password', [PasswordResetController::class, 'store'])
        ->middleware('throttle:10,1')
        ->name('password.update');
});
Route::post('/logout', [WebAuthController::class, 'destroy'])->middleware('auth');

Route::get('/oauth/email/{provider}/callback', EmailOAuthCallbackController::class)
    ->middleware('throttle:30,1')
    ->name('oauth.email.callback');

Route::middleware(['auth', 'admin'])->group(function (): void {
    Route::get('/dashboard', [AdminController::class, 'dashboard']);
    Route::get('/admin/users', [AdminController::class, 'users']);
    Route::post('/admin/users', [AdminController::class, 'storeUser']);
    Route::get('/admin/accounts', [AdminController::class, 'accounts']);
    Route::get('/admin/transactions', [AdminController::class, 'transactions']);
    Route::get('/admin/email-connections', [AdminController::class, 'emailConnections']);
    Route::delete('/admin/email-connections/{emailConnection}', [AdminController::class, 'disconnectEmail']);
});

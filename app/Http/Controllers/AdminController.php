<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\EmailConnection;
use App\Models\Transaction;
use App\Models\User;
use App\Services\EmailOAuthService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rules\Password;
use Illuminate\View\View;

class AdminController extends Controller
{
    public function dashboard(): View
    {
        return view('admin.dashboard', ['counts' => [
            'users' => User::count(),
            'accounts' => Account::count(),
            'transactions' => Transaction::count(),
            'connections' => EmailConnection::count(),
        ]]);
    }

    public function users(): View
    {
        return view('admin.users', ['users' => User::latest()->paginate(25)]);
    }

    public function storeUser(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email'],
            'password' => ['required', 'confirmed', Password::min(12)],
        ]);
        User::create([
            'name' => $data['name'],
            'email' => strtolower($data['email']),
            'password' => Hash::make($data['password']),
        ]);

        return redirect('/admin/users')->with('status', 'Usuario creado.');
    }

    public function accounts(): View
    {
        return view('admin.accounts', ['accounts' => Account::with('user')->latest()->paginate(25)]);
    }

    public function transactions(): View
    {
        return view('admin.transactions', ['transactions' => Transaction::with(['user', 'account'])->latest('occurred_at')->paginate(25)]);
    }

    public function emailConnections(): View
    {
        return view('admin.email-connections', ['connections' => EmailConnection::with('user')->latest()->paginate(25)]);
    }

    public function disconnectEmail(EmailConnection $emailConnection, EmailOAuthService $oauth): RedirectResponse
    {
        $oauth->disconnect($emailConnection->user, $emailConnection->provider);

        return redirect('/admin/email-connections')->with('status', 'Conexión desconectada.');
    }
}

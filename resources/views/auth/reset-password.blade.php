@extends('layouts.app')
@section('title','Restablecer contraseña')
@section('content')
<div class="card login">
    <h1>Restablecer contraseña</h1>
    <p>Define una contraseña nueva para Wallet Finanzas.</p>
    <form method="POST" action="{{ route('password.update') }}">
        @csrf
        <input type="hidden" name="token" value="{{ $token }}">
        <label for="email">Correo</label>
        <input id="email" type="email" name="email" value="{{ old('email', $email) }}" required autocomplete="username">
        @error('email')<p class="error">{{ $message }}</p>@enderror
        <label for="password">Contraseña nueva</label>
        <input id="password" type="password" name="password" required minlength="10" autocomplete="new-password">
        @error('password')<p class="error">{{ $message }}</p>@enderror
        <label for="password_confirmation">Confirmar contraseña</label>
        <input id="password_confirmation" type="password" name="password_confirmation" required minlength="10" autocomplete="new-password">
        <button type="submit">Guardar contraseña</button>
    </form>
</div>
@endsection

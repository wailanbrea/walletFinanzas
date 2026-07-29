@extends('layouts.app')
@section('title','Acceso administrativo')
@section('content')
<div class="card login"><h1>Acceso administrativo</h1><p>Gestiona Wallet Finanzas de forma segura.</p><form method="POST" action="/login">@csrf<label for="email">Correo</label><input id="email" type="email" name="email" value="{{ old('email') }}" required autocomplete="username">@error('email')<p class="error">{{ $message }}</p>@enderror<label for="password">Contraseña</label><input id="password" type="password" name="password" required autocomplete="current-password"><label><input style="width:auto" type="checkbox" name="remember" value="1"> Recordarme</label><button type="submit">Entrar</button></form></div>
@endsection

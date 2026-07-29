@extends('layouts.app')
@section('title','Correo conectado')
@section('content')
<div class="card login">
    <h1>{{ $provider }} conectado</h1>
    <p>La autorización se completó correctamente. Volviendo a Wallet Finanzas…</p>
    <p><a id="return-to-wallet" class="button" href="{{ $intentUri }}">Volver a Wallet</a></p>
    <p><small>Si Wallet no se abre automáticamente, pulsa el botón.</small></p>
</div>
<script>
    window.setTimeout(function () {
        window.location.replace(@json($intentUri));
    }, 100);
</script>
@endsection

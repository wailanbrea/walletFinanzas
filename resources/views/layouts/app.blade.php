<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Wallet Finanzas')</title>
    <style>
        :root{font-family:Inter,system-ui,sans-serif;color:#172033;background:#f4f7fb}*{box-sizing:border-box}body{margin:0}a{color:#1358a7;text-decoration:none}.shell{max-width:1180px;margin:auto;padding:1rem}.top{background:#102a43;color:#fff}.top .shell{display:flex;gap:1rem;align-items:center;flex-wrap:wrap}.top a{color:#fff}.top nav{display:flex;gap:1rem;flex:1;flex-wrap:wrap}.top form{margin:0}button,.button{border:0;border-radius:.5rem;padding:.65rem .9rem;background:#1769aa;color:#fff;cursor:pointer}button.danger{background:#b42318}input{width:100%;padding:.7rem;border:1px solid #cbd5e1;border-radius:.45rem}label{display:block;margin:.7rem 0 .25rem}.card{background:#fff;border-radius:.8rem;padding:1rem;box-shadow:0 2px 12px #102a4312;margin:1rem 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:1rem}.metric{font-size:2rem;font-weight:700}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:.7rem;border-bottom:1px solid #e2e8f0;white-space:nowrap}.alert{padding:.8rem;border-radius:.5rem;background:#dcfce7}.error{color:#b42318}.login{max-width:430px;margin:8vh auto}@media(max-width:600px){.shell{padding:.7rem}th,td{padding:.55rem}.top nav{order:3;width:100%}}
    </style>
</head>
<body>
@auth
<header class="top"><div class="shell"><strong>Wallet Finanzas</strong><nav><a href="/dashboard">Resumen</a><a href="/admin/users">Usuarios</a><a href="/admin/accounts">Cuentas</a><a href="/admin/transactions">Transacciones</a><a href="/admin/email-connections">Correo</a></nav><form method="POST" action="/logout">@csrf<button type="submit">Salir</button></form></div></header>
@endauth
<main class="shell">@if(session('status'))<p class="alert">{{ session('status') }}</p>@endif @yield('content')</main>
</body></html>

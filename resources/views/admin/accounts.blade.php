@extends('layouts.app')
@section('title','Cuentas')
@section('content')<h1>Cuentas</h1><div class="card table-wrap"><table><thead><tr><th>Usuario</th><th>Cuenta</th><th>Institución</th><th>Saldo</th><th>Estado</th></tr></thead><tbody>@foreach($accounts as $account)<tr><td>{{ $account->user->email }}</td><td>{{ $account->name }}</td><td>{{ $account->institution_name ?? '—' }}</td><td>{{ number_format($account->balance / 100, 2) }} {{ $account->currency }}</td><td>{{ $account->is_active ? 'Activa' : 'Inactiva' }}</td></tr>@endforeach</tbody></table></div>{{ $accounts->links() }}@endsection

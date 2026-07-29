@extends('layouts.app')
@section('title','Panel de administración')
@section('content')<h1>Panel de administración</h1><div class="grid">@foreach($counts as $label=>$count)<div class="card"><div class="metric">{{ $count }}</div><div>{{ ucfirst($label) }}</div></div>@endforeach</div>@endsection

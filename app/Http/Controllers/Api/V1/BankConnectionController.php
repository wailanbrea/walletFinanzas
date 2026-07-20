<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\V1\BankConnectionResource;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class BankConnectionController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        return BankConnectionResource::collection(
            $request->user()->bankConnections()->latest()->get()
        );
    }
}

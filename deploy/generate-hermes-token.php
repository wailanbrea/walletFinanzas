<?php

use App\Models\User;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;

require dirname(__DIR__).'/vendor/autoload.php';

$app = require dirname(__DIR__).'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

try {
    $token = DB::transaction(function () {
        $user = User::query()->findOrFail(1);
        $user->tokens()->where('name', 'hermes-finanzas')->delete();

        return $user->createToken('hermes-finanzas', [
            'agent.read',
            'agent.write',
            'agent.delete',
        ])->plainTextToken;
    });

    echo $token.PHP_EOL;
} catch (Throwable $exception) {
    fwrite(STDERR, 'No se pudo generar el token: '.$exception->getMessage().PHP_EOL);
    exit(1);
}

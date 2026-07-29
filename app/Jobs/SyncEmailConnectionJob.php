<?php

namespace App\Jobs;

use App\Services\EmailSyncService;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Bus\Dispatchable;
use Illuminate\Queue\InteractsWithQueue;
use Illuminate\Queue\SerializesModels;

/**
 * Ejecuta la sincronización de correos fuera del request HTTP (evita pinchar un worker
 * de PHP-FPM durante minutos con ~50 llamadas externas). El cliente sondea el estado
 * del EmailSyncRun devuelto por el endpoint.
 */
class SyncEmailConnectionJob implements ShouldQueue
{
    use Dispatchable;
    use InteractsWithQueue;
    use Queueable;
    use SerializesModels;

    public int $timeout = 300;

    public int $tries = 1;

    public function __construct(public int $syncRunId) {}

    public function handle(EmailSyncService $service): void
    {
        $service->process($this->syncRunId);
    }
}

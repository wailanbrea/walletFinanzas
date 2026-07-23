<?php

namespace App\Jobs;

use App\Models\EmailSyncRun;
use App\Services\EmailMailboxScanner;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Queue\Queueable;
use Throwable;

class SyncEmailConnection implements ShouldQueue
{
    use Queueable;

    public int $tries = 3;

    public array $backoff = [10, 30];

    public int $timeout = 120;

    public function __construct(public int $runId) {}

    public function handle(EmailMailboxScanner $scanner): void
    {
        $run = EmailSyncRun::query()->with('connection')->find($this->runId);
        if (! $run || ! $run->connection) {
            return;
        }
        $run->update(['status' => 'running', 'started_at' => now(), 'error_code' => null]);

        $counts = $scanner->scan($run->connection);
        $run->update($counts + ['status' => 'completed', 'finished_at' => now()]);
    }

    public function failed(?Throwable $exception): void
    {
        EmailSyncRun::query()->whereKey($this->runId)->update([
            'status' => 'failed',
            'error_code' => 'email_provider_request_failed',
            'finished_at' => now(),
        ]);
    }
}

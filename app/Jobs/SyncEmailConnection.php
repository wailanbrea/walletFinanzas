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

    public int $timeout = 75;

    public function __construct(public int $runId) {}

    public function handle(EmailMailboxScanner $scanner): void
    {
        $run = EmailSyncRun::query()->with('connection')->find($this->runId);
        if (! $run || ! $run->connection) {
            return;
        }
        $claimed = EmailSyncRun::query()
            ->whereKey($run->id)
            ->where(function ($query): void {
                $query->where('status', 'queued')
                    ->orWhere(function ($query): void {
                        $query->where('status', 'running')
                            ->where('updated_at', '<=', now()->subSeconds($this->timeout + 5));
                    });
            })
            ->update([
                'status' => 'running',
                'started_at' => $run->started_at ?? now(),
                'error_code' => null,
            ]);
        if ($claimed !== 1) {
            return;
        }
        $run->refresh();

        try {
            $counts = $scanner->scan($run->connection, $run->sync_from_at);
        } catch (Throwable $exception) {
            if ($exception->getMessage() === 'email_reauthorization_required') {
                EmailSyncRun::query()->whereKey($run->id)->where('status', 'running')->update([
                    'status' => 'failed',
                    'error_code' => 'email_reauthorization_required',
                    'finished_at' => now(),
                ]);

                return;
            }
            EmailSyncRun::query()->whereKey($run->id)->where('status', 'running')->update(['status' => 'queued']);
            throw $exception;
        }
        $hasMore = (bool) ($counts['has_more'] ?? false);
        unset($counts['has_more']);
        $totals = [];
        foreach (['messages_discovered', 'messages_created', 'candidates_created', 'conversions_backfilled'] as $field) {
            $totals[$field] = (int) $run->{$field} + (int) ($counts[$field] ?? 0);
        }
        if ($hasMore) {
            $run->update($totals + ['status' => 'queued', 'finished_at' => null]);
            self::dispatch($run->id);

            return;
        }

        $run->update($totals + ['status' => 'completed', 'finished_at' => now()]);
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

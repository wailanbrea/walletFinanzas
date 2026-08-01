<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('email_mailboxes', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('provider', 20);
            $table->string('email');
            $table->date('sync_from_date')->nullable();
            $table->dateTime('sync_from_at')->nullable();
            $table->dateTime('backfill_before_at')->nullable();
            $table->text('backfill_cursor')->nullable();
            $table->dateTime('backfill_completed_at')->nullable();
            $table->dateTime('incremental_from_at')->nullable();
            $table->dateTime('incremental_before_at')->nullable();
            $table->text('incremental_cursor')->nullable();
            $table->timestamps();
            $table->unique(['user_id', 'provider', 'email'], 'email_mailboxes_identity');
        });

        Schema::table('email_connections', function (Blueprint $table): void {
            $table->foreignUuid('email_mailbox_id')->nullable()->after('user_id')
                ->constrained('email_mailboxes')->nullOnDelete();
        });

        Schema::table('email_sync_runs', function (Blueprint $table): void {
            $table->dateTime('sync_from_at')->nullable()->after('provider');
        });

        Schema::create('email_message_decisions', function (Blueprint $table): void {
            $table->id();
            $table->foreignUuid('email_mailbox_id')->constrained('email_mailboxes')->cascadeOnDelete();
            $table->string('provider_message_id', 255);
            $table->string('status', 20);
            $table->string('category', 120)->nullable();
            $table->dateTime('decided_at');
            $table->timestamps();
            $table->unique(['email_mailbox_id', 'provider_message_id'], 'email_message_decisions_unique');
        });

        DB::table('email_connections')->orderBy('id')->each(function (object $connection): void {
            $email = Str::lower(trim((string) $connection->email));
            $mailbox = DB::table('email_mailboxes')
                ->where('user_id', $connection->user_id)
                ->where('provider', $connection->provider)
                ->where('email', $email)
                ->first();
            $mailboxId = $mailbox?->id ?? (string) Str::uuid();
            if (! $mailbox) {
                DB::table('email_mailboxes')->insert([
                    'id' => $mailboxId,
                    'user_id' => $connection->user_id,
                    'provider' => $connection->provider,
                    'email' => $email,
                    'created_at' => now(),
                    'updated_at' => now(),
                ]);
            }
            DB::table('email_connections')->where('id', $connection->id)->update([
                'email_mailbox_id' => $mailboxId,
            ]);
        });

        // The legacy connection was reused when an OAuth account changed, so its raw
        // messages cannot be attributed to a mailbox safely. Financial transactions are
        // stored separately; only the rebuildable email import cache is discarded.
        $cutover = now();
        DB::table('provider_messages')->delete();
        DB::table('email_connections')->update(['last_synced_at' => $cutover]);
        DB::table('email_mailboxes')->update([
            'sync_from_date' => $cutover->toDateString(),
            'sync_from_at' => $cutover,
            'backfill_before_at' => null,
            'backfill_cursor' => null,
            'backfill_completed_at' => $cutover,
            'incremental_from_at' => null,
            'incremental_before_at' => null,
            'incremental_cursor' => null,
            'updated_at' => $cutover,
        ]);
    }

    public function down(): void
    {
        Schema::dropIfExists('email_message_decisions');
        Schema::table('email_sync_runs', fn (Blueprint $table) => $table->dropColumn('sync_from_at'));
        Schema::table('email_connections', function (Blueprint $table): void {
            $table->dropConstrainedForeignId('email_mailbox_id');
        });
        Schema::dropIfExists('email_mailboxes');
    }
};

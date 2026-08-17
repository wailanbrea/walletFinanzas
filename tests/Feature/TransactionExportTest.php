<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class TransactionExportTest extends TestCase
{
    use RefreshDatabase;

    public function test_export_csv_returns_correct_columns_and_bom(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -2500,
            'currency' => 'DOP',
            'description' => 'Supermercado',
            'occurred_at' => '2026-07-20 14:30:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => 50000,
            'currency' => 'DOP',
            'description' => 'Nómina',
            'occurred_at' => '2026-07-25 09:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $response->assertHeader('Content-Type', 'text/csv; charset=utf-8');
        $disposition = $response->headers->get('Content-Disposition');
        $this->assertStringContainsString('attachment', $disposition);
        $this->assertStringContainsString('transactions_', $disposition);
        $this->assertStringContainsString('.csv', $disposition);

        $body = $response->content();
        // UTF-8 BOM: 3 raw bytes, not characters
        $this->assertEquals("\xEF\xBB\xBF", substr($body, 0, 3));

        // Strip BOM and parse lines
        $csv = ltrim($body, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($csv));

        // Header
        $this->assertEquals('id,account_id,account_name,amount,currency,description,occurred_at,status', $lines[0]);

        // 2 transactions + header = 3 lines
        $this->assertCount(3, $lines);

        // Data rows (sorted by occurred_at DESC)
        $this->assertStringContainsString('50000', $lines[1]); // Nómina (later date)
        $this->assertStringContainsString('-2500', $lines[2]); // Supermercado (earlier date)
    }

    public function test_export_csv_filters_by_account_id(): void
    {
        $owner = User::factory()->create();
        $accountA = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        $accountB = Account::create([
            'user_id' => $owner->id,
            'name' => 'Banco',
            'balance' => 500000,
            'currency' => 'USD',
            'country_code' => 'US',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $accountA->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Gasto A',
            'occurred_at' => '2026-07-20 10:00:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $accountB->id,
            'amount' => 5000,
            'currency' => 'USD',
            'description' => 'Ingreso B',
            'occurred_at' => '2026-07-20 12:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv?account_id=' . $accountA->id);

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // Header + 1 row
        $this->assertCount(2, $lines);
        $this->assertStringContainsString('Gasto A', $lines[1]);
        $this->assertStringNotContainsString('Ingreso B', $lines[1]);
    }

    public function test_export_csv_filters_by_date_range(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Junio',
            'occurred_at' => '2026-06-15 10:00:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -2000,
            'currency' => 'DOP',
            'description' => 'Julio',
            'occurred_at' => '2026-07-20 10:00:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -3000,
            'currency' => 'DOP',
            'description' => 'Agosto',
            'occurred_at' => '2026-08-10 10:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv?from=2026-07-01&to=2026-07-31');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // Header + 1 row (only July)
        $this->assertCount(2, $lines);
        $this->assertStringContainsString('Julio', $lines[1]);
        $this->assertStringNotContainsString('Junio', $body);
        $this->assertStringNotContainsString('Agosto', $body);
    }

    public function test_export_csv_filters_by_currency(): void
    {
        $owner = User::factory()->create();
        $accountDop = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo DOP',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        $accountUsd = Account::create([
            'user_id' => $owner->id,
            'name' => 'Banco USD',
            'balance' => 500000,
            'currency' => 'USD',
            'country_code' => 'US',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $accountDop->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Gasto DOP',
            'occurred_at' => '2026-07-20 10:00:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $accountUsd->id,
            'amount' => 5000,
            'currency' => 'USD',
            'description' => 'Ingreso USD',
            'occurred_at' => '2026-07-20 12:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv?currency=USD');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        $this->assertCount(2, $lines);
        $this->assertStringContainsString('Ingreso USD', $lines[1]);
        $this->assertStringNotContainsString('Gasto DOP', $body);
    }

    public function test_export_csv_requires_authentication(): void
    {
        $this->getJson('/api/v1/transactions/export.csv')
            ->assertUnauthorized();
    }

    public function test_export_csv_returns_empty_for_user_with_no_transactions(): void
    {
        $owner = User::factory()->create();
        Account::create([
            'user_id' => $owner->id,
            'name' => 'Vacía',
            'balance' => 0,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // Only header
        $this->assertCount(1, $lines);
        $this->assertEquals('id,account_id,account_name,amount,currency,description,occurred_at,status', $lines[0]);
    }

    public function test_export_csv_escapes_quotes_in_description(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Comida "especial" y más',
            'occurred_at' => '2026-07-20 10:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // Quotes should be doubled per CSV spec
        $this->assertStringContainsString('Comida ""especial"" y más', $lines[1]);
    }

    public function test_export_csv_uses_iso_8601_format_for_occurred_at(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Pago puntual',
            'occurred_at' => '2026-07-20 14:30:45',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // occurred_at should be ISO 8601 UTC: 2026-07-20T14:30:45Z
        $this->assertMatchesRegularExpression(
            '/2026-07-20T14:30:45Z/',
            $lines[1]
        );
    }

    public function test_export_csv_uses_minor_units_for_amount(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => 1234567,
            'currency' => 'DOP',
            'description' => 'Importe grande',
            'occurred_at' => '2026-07-20 10:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // Should be integer, not decimal
        $this->assertStringContainsString('1234567', $lines[1]);
        $this->assertStringNotContainsString('1234.567', $lines[1]);
    }

    public function test_export_csv_order_is_occurred_at_desc(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Primero',
            'occurred_at' => '2026-07-01 10:00:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -2000,
            'currency' => 'DOP',
            'description' => 'Segundo',
            'occurred_at' => '2026-07-15 10:00:00',
            'status' => 'completed',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -3000,
            'currency' => 'DOP',
            'description' => 'Tercero',
            'occurred_at' => '2026-07-30 10:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        // Tercero (Jul 30) first, then Segundo (Jul 15), then Primero (Jul 1)
        $this->assertStringContainsString('Tercero', $lines[1]);
        $this->assertStringContainsString('Segundo', $lines[2]);
        $this->assertStringContainsString('Primero', $lines[3]);
    }

    public function test_export_csv_limits_to_10000_rows(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        // Create 10,001 transactions in batches with UUIDs for SQLite
        $batchSize = 100;
        $total = 10001;
        for ($batch = 0; $batch < ceil($total / $batchSize); $batch++) {
            $transactions = [];
            for ($i = $batch * $batchSize; $i < min($total, ($batch + 1) * $batchSize); $i++) {
                $transactions[] = [
                    'id' => (string) Str::uuid(),
                    'user_id' => $owner->id,
                    'account_id' => $account->id,
                    'amount' => -100,
                    'currency' => 'DOP',
                    'description' => "Transacción $i",
                    'occurred_at' => now()->subDays($i % 365)->format('Y-m-d H:i:s'),
                    'status' => 'completed',
                ];
            }
            Transaction::insert($transactions);
        }

        Sanctum::actingAs($owner);
        $this->getJson('/api/v1/transactions/export.csv')
            ->assertStatus(400);
    }

    public function test_export_csv_allows_exactly_10000_rows(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        // Create 10,000 transactions in batches with UUIDs for SQLite
        $batchSize = 100;
        for ($batch = 0; $batch < 100; $batch++) {
            $transactions = [];
            for ($i = $batch * $batchSize; $i < ($batch + 1) * $batchSize; $i++) {
                $transactions[] = [
                    'id' => (string) Str::uuid(),
                    'user_id' => $owner->id,
                    'account_id' => $account->id,
                    'amount' => -100,
                    'currency' => 'DOP',
                    'description' => "Transacción $i",
                    'occurred_at' => now()->subDays($i % 365)->format('Y-m-d H:i:s'),
                    'status' => 'completed',
                ];
            }
            Transaction::insert($transactions);
        }

        Sanctum::actingAs($owner);
        $this->getJson('/api/v1/transactions/export.csv')
            ->assertStatus(200);
    }

    public function test_export_csv_includes_account_name_from_relationship(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Mi Cuenta Importante',
            'balance' => 100000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Transaction::create([
            'user_id' => $owner->id,
            'account_id' => $account->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Gasto',
            'occurred_at' => '2026-07-20 10:00:00',
            'status' => 'completed',
        ]);

        Sanctum::actingAs($owner);
        $response = $this->getJson('/api/v1/transactions/export.csv');

        $response->assertStatus(200);
        $content = $response->content();
        $body = ltrim($content, "\xEF\xBB\xBF");
        $lines = explode("\r\n", trim($body));

        $this->assertStringContainsString('Mi Cuenta Importante', $lines[1]);
    }
}

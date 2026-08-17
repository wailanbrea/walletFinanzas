<?php

namespace Tests\Feature;

use App\Models\User;
use App\Models\WalletSyncResource;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class WalletSyncTransactionTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Sanctum::actingAs(User::factory()->create());
        WalletSyncResource::flushEventListeners();
    }

    protected function tearDown(): void
    {
        WalletSyncResource::flushEventListeners();
        parent::tearDown();
    }

    // --------------------------------------------------------------------
    // ROLLBACK: updateDebt — falla en la escritura (UPDATE)
    // --------------------------------------------------------------------

    /**
     * updateDebt envuelve la actualización en DB::transaction.
     * Si el UPDATE falla, la transacción se revierte y la deuda queda
     * intacta.
     */
    public function test_update_debt_rolls_back_on_write_failure(): void
    {
        $debtId = (string) \Illuminate\Support\Str::uuid();

        // Crear la deuda.
        $this->postJson('/api/v1/debts', [
            'id' => $debtId,
            'name' => 'Préstamo de Ana',
            'description' => 'Para probar rollback',
            'direction' => 'I_OWE',
            'total_amount' => 10000,
            'paid_amount' => 2000,
            'due_date' => null,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        $this->assertDatabaseHas('debts', [
            'id' => $debtId,
            'paid_amount' => 2000,
        ]);

        // Lanzar excepción DESPUÉS de que el UPDATE ya se ejecutó (evento saved,
        // no saving), para que haya algo real que revertir.
        WalletSyncResource::saved(function (WalletSyncResource $model) {
            if ($model->getTable() === 'debts') {
                throw new \RuntimeException('Simulated write failure');
            }
        });

        $this->patchJson("/api/v1/debts/{$debtId}", [
            'paid_amount' => 5000,
        ])->assertStatus(500);

        // La deuda no cambió porque la transacción se revirtió.
        $this->assertDatabaseHas('debts', [
            'id' => $debtId,
            'paid_amount' => 2000,
        ]);
    }

    // --------------------------------------------------------------------
    // ROLLBACK: store (categories) — falla en borrado en cascada
    // --------------------------------------------------------------------

    /**
     * store para categories con is_deleted=true hace:
     *   1) UPDATE de la categoría (vía model, dispara saving)
     *   2) UPDATE bulk de budgets (query builder, sin model events)
     *   3) UPDATE bulk de planned_payments (query builder, sin model events)
     *
     * Como los updates en cascada son query-builder bulk (sin model events),
     * no podemos inyectar un fallo en el paso 2 o 3 con model events.
     * En su lugar, inyectamos el fallo en el paso 1 (el UPDATE de la categoría)
     * y verificamos que la categoría no queda marcada como borrada.
     */
    public function test_store_category_rollback_on_delete_failure(): void
    {
        $catId = (string) \Illuminate\Support\Str::uuid();
        $budgetId = (string) \Illuminate\Support\Str::uuid();

        // Crear categoría y presupuesto asociado.
        $this->postJson('/api/v1/categories', [
            'id' => $catId,
            'name' => 'Comida',
            'icon' => 'food',
            'color_hex' => '#FF0000',
            'type' => 'EXPENSE',
            'is_deleted' => false,
        ])->assertCreated();

        $this->postJson('/api/v1/budgets', [
            'id' => $budgetId,
            'category_id' => $catId,
            'limit_amount' => 50000,
            'spent_amount' => 0,
            'period' => 'MONTHLY',
            'is_deleted' => false,
        ])->assertCreated();

        // Lanzar excepción DESPUÉS de que el UPDATE de la categoría ya se ejecutó
        // (evento saved, no saving), para que haya algo real que revertir.
        WalletSyncResource::saved(function (WalletSyncResource $model) {
            if ($model->getTable() === 'categories' && $model->is_deleted) {
                throw new \RuntimeException('Simulated category delete failure');
            }
        });

        // Borrar la categoría (store es upsert: POST con id existente = update).
        $this->postJson('/api/v1/categories', [
            'id' => $catId,
            'name' => 'Comida',
            'icon' => 'food',
            'color_hex' => '#FF0000',
            'type' => 'EXPENSE',
            'is_deleted' => true,
        ])->assertStatus(500);

        // Verificar rollback: la categoría no fue borrada.
        $this->assertDatabaseHas('categories', [
            'id' => $catId,
            'is_deleted' => false,
        ]);

        // Y el presupuesto asociado tampoco (nunca llegó al bulk update).
        $this->assertDatabaseHas('budgets', [
            'id' => $budgetId,
            'is_deleted' => false,
        ]);
    }

    // --------------------------------------------------------------------
    // ROLLBACK: store debt — falla en el INSERT
    // --------------------------------------------------------------------

    public function test_store_debt_rolls_back_on_insert_failure(): void
    {
        $debtId = (string) \Illuminate\Support\Str::uuid();

        // created (no creating): dispara justo DESPUÉS del INSERT, para que
        // haya una fila real que el rollback deba eliminar.
        WalletSyncResource::created(function (WalletSyncResource $model) {
            if ($model->getTable() === 'debts') {
                throw new \RuntimeException('Simulated insert failure');
            }
        });

        $this->postJson('/api/v1/debts', [
            'id' => $debtId,
            'name' => 'Deuda que no existe',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 5000,
            'paid_amount' => 0,
            'due_date' => null,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertStatus(500);

        $this->assertDatabaseMissing('debts', ['id' => $debtId]);
    }

    // --------------------------------------------------------------------
    // SUCCESS: flujos normales
    // --------------------------------------------------------------------

    public function test_update_debt_closes_successfully_when_paid_equals_total(): void
    {
        $debtId = (string) \Illuminate\Support\Str::uuid();

        $this->postJson('/api/v1/debts', [
            'id' => $debtId,
            'name' => 'Préstamo pagado',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 10000,
            'paid_amount' => 5000,
            'due_date' => null,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        $this->patchJson("/api/v1/debts/{$debtId}", [
            'paid_amount' => 10000,
            'is_closed' => true,
        ])->assertOk()
            ->assertJsonPath('data.paid_amount', 10000)
            ->assertJsonPath('data.is_closed', true);

        $this->assertDatabaseHas('debts', [
            'id' => $debtId,
            'paid_amount' => 10000,
            'is_closed' => true,
        ]);
    }

    public function test_store_debt_creates_successfully(): void
    {
        $debtId = (string) \Illuminate\Support\Str::uuid();

        $this->postJson('/api/v1/debts', [
            'id' => $debtId,
            'name' => 'Nueva deuda',
            'description' => 'Descripción de prueba',
            'direction' => 'OWED_TO_ME',
            'total_amount' => 20000,
            'paid_amount' => 0,
            'due_date' => null,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        $this->assertDatabaseHas('debts', [
            'id' => $debtId,
            'name' => 'Nueva deuda',
            'direction' => 'OWED_TO_ME',
            'total_amount' => 20000,
            'paid_amount' => 0,
            'is_closed' => false,
        ]);
    }
}

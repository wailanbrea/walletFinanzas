<?php

namespace Tests\Feature;

use App\Models\EmailMessage;
use App\Models\FinancialTransactionCandidate;
use App\Models\User;
use App\Services\EmailClassificationLearningService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class EmailCandidateReviewApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_user_classifies_candidate_and_category_is_learned_for_future_mail_from_sender(): void
    {
        [$owner, $candidate, $message] = $this->candidate('Compra aprobada #12345');
        Sanctum::actingAs($owner, ['wallet']);

        $this->patchJson('/api/v1/email-candidates/'.$candidate->id, [
            'action' => 'categorize',
            'category' => 'Compras en línea',
            'learn' => true,
        ])->assertOk()
            ->assertJsonPath('data.status', 'classified')
            ->assertJsonPath('data.category_suggestion', 'Compras en línea');

        $this->assertDatabaseHas('email_classification_rules', [
            'user_id' => $owner->id,
            'provider' => 'gmail',
            'type' => 'sender_category',
            'sender_hash' => hash('sha256', strtolower($message->sender_email)),
            'category' => 'Compras en línea',
        ]);

        $learning = app(EmailClassificationLearningService::class);
        $classification = $learning->apply(
            $learning->rulesFor($owner),
            ['provider' => 'gmail', 'sender_email' => $message->sender_email, 'subject' => 'Compra aprobada #98765'],
            ['category_suggestion' => null, 'reasons' => []]
        );

        $this->assertSame('Compras en línea', $classification['category_suggestion']);
        $this->assertContains('learned_category_rule', $classification['reasons']);
        $unrelated = $learning->apply(
            $learning->rulesFor($owner),
            ['provider' => 'gmail', 'sender_email' => $message->sender_email, 'subject' => 'Reembolso procesado'],
            ['category_suggestion' => null, 'reasons' => []]
        );
        $this->assertNull($unrelated['category_suggestion']);
    }

    public function test_user_dismisses_non_transaction_and_subject_template_is_learned_without_blocking_other_subjects(): void
    {
        [$owner, $candidate, $message] = $this->candidate('Amazon encontró algo que te gustará');
        Sanctum::actingAs($owner, ['wallet']);

        $this->patchJson('/api/v1/email-candidates/'.$candidate->id, [
            'action' => 'dismiss',
            'learn' => true,
        ])->assertOk()->assertJsonPath('data.status', 'dismissed');

        $learning = app(EmailClassificationLearningService::class);
        $rules = $learning->rulesFor($owner);

        $this->assertNull($learning->apply(
            $rules,
            ['provider' => 'gmail', 'sender_email' => $message->sender_email, 'subject' => 'Amazon encontró algo que te gustará'],
            ['category_suggestion' => null, 'reasons' => []]
        ));
        $this->assertNotNull($learning->apply(
            $rules,
            ['provider' => 'microsoft', 'sender_email' => $message->sender_email, 'subject' => 'Amazon encontró algo que te gustará'],
            ['category_suggestion' => null, 'reasons' => []]
        ));
        $this->assertNotNull($learning->apply(
            $rules,
            ['provider' => 'gmail', 'sender_email' => $message->sender_email, 'subject' => 'Pago aprobado para tu pedido'],
            ['category_suggestion' => null, 'reasons' => []]
        ));
    }

    public function test_user_cannot_review_another_users_candidate(): void
    {
        [, $candidate] = $this->candidate();
        Sanctum::actingAs(User::factory()->create(), ['wallet']);

        $this->patchJson('/api/v1/email-candidates/'.$candidate->id, [
            'action' => 'dismiss',
            'learn' => true,
        ])->assertNotFound();
    }

    /** @return array{User,FinancialTransactionCandidate,EmailMessage} */
    private function candidate(string $subject = 'Compra aprobada'): array
    {
        $owner = User::factory()->create();
        $connection = $owner->emailConnections()->create([
            'provider' => 'gmail',
            'email' => 'owner@example.com',
            'access_token' => 'access-token',
            'status' => 'connected',
            'connected_at' => now(),
        ]);
        $message = EmailMessage::create([
            'user_id' => $owner->id,
            'email_connection_id' => $connection->id,
            'provider_message_id' => fake()->uuid(),
            'sender_email' => 'store-news@amazon.example',
            'sender_name' => 'Amazon',
            'subject' => $subject,
            'received_at' => now(),
            'body_excerpt' => 'Contenido mínimo',
            'content_hash' => hash('sha256', fake()->uuid()),
            'status' => 'processed',
        ]);
        $candidate = $message->candidate()->create([
            'user_id' => $owner->id,
            'provider' => 'gmail',
            'merchant' => 'Amazon',
            'amount' => -2999,
            'currency' => 'USD',
            'direction' => 'expense',
            'occurred_at' => now(),
            'confidence' => 80,
            'reasons' => ['financial_keyword:compra'],
            'status' => 'pending',
        ]);

        return [$owner, $candidate, $message];
    }
}

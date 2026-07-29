<?php

namespace App\Services;

use App\Models\EmailClassificationRule;
use App\Models\FinancialTransactionCandidate;
use App\Models\User;
use Illuminate\Support\Collection;
use Illuminate\Support\Str;

class EmailClassificationLearningService
{
    /** @return Collection<int, EmailClassificationRule> */
    public function rulesFor(User $user): Collection
    {
        return EmailClassificationRule::query()->where('user_id', $user->id)->get();
    }

    /**
     * @param  Collection<int, EmailClassificationRule>  $rules
     * @param  array{provider:string,sender_email:?string,subject:?string}  $message
     * @param  array<string,mixed>|null  $classification
     * @return array<string,mixed>|null
     */
    public function apply(Collection $rules, array $message, ?array $classification): ?array
    {
        if (! $classification || ! $message['sender_email']) {
            return $classification;
        }

        $senderHash = $this->senderHash($message['sender_email']);
        $subjectFingerprint = $this->subjectFingerprint($message['subject'] ?? '');
        $ignored = $rules->contains(fn (EmailClassificationRule $rule): bool => $rule->type === 'ignore_subject'
            && $rule->provider === $message['provider']
            && hash_equals($rule->sender_hash, $senderHash)
            && hash_equals($rule->subject_fingerprint, $subjectFingerprint)
        );
        if ($ignored) {
            return null;
        }

        $categoryRule = $rules->first(fn (EmailClassificationRule $rule): bool => $rule->type === 'sender_category'
            && $rule->provider === $message['provider']
            && hash_equals($rule->sender_hash, $senderHash)
            && hash_equals($rule->subject_fingerprint, $subjectFingerprint)
        );
        if ($categoryRule?->category) {
            $classification['category_suggestion'] = $categoryRule->category;
            $classification['reasons'][] = 'learned_category_rule';
        }

        return $classification;
    }

    public function learn(FinancialTransactionCandidate $candidate, string $action, ?string $category): void
    {
        $candidate->loadMissing('message');
        $sender = $candidate->message?->sender_email;
        if (! $sender) {
            return;
        }

        $attributes = [
            'user_id' => $candidate->user_id,
            'provider' => $candidate->provider,
            'sender_hash' => $this->senderHash($sender),
        ];

        if ($action === 'dismiss') {
            EmailClassificationRule::updateOrCreate([
                ...$attributes,
                'type' => 'ignore_subject',
                'subject_fingerprint' => $this->subjectFingerprint($candidate->message?->subject ?? ''),
            ], ['category' => null]);

            return;
        }

        EmailClassificationRule::updateOrCreate([
            ...$attributes,
            'type' => 'sender_category',
            'subject_fingerprint' => $this->subjectFingerprint($candidate->message?->subject ?? ''),
        ], ['category' => $category]);
    }

    private function senderHash(string $sender): string
    {
        return hash('sha256', Str::lower(trim($sender)));
    }

    private function subjectFingerprint(string $subject): string
    {
        $normalized = Str::lower(Str::ascii(html_entity_decode(strip_tags($subject), ENT_QUOTES | ENT_HTML5, 'UTF-8')));
        $normalized = preg_replace('/(?:rd|us)?\$?\s*\d+(?:[.,]\d+)?/u', ' amount ', $normalized);
        $normalized = preg_replace('/[^a-z0-9]+/u', ' ', (string) $normalized);
        $normalized = trim((string) preg_replace('/\s+/u', ' ', (string) $normalized));

        return hash('sha256', $normalized);
    }
}

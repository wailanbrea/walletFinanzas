<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\V1\EmailCandidateResource;
use App\Models\FinancialTransactionCandidate;
use App\Services\EmailClassificationLearningService;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Resources\Json\JsonResource;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

class EmailCandidateController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        $validated = $request->validate([
            'status' => ['sometimes', 'string', Rule::in(['pending', 'classified', 'dismissed'])],
            'provider' => ['sometimes', 'string', Rule::in(['gmail', 'microsoft'])],
        ]);
        $candidates = $request->user()
            ->emailCandidates()
            ->with('message:id,subject,sender_email')
            ->where('status', $validated['status'] ?? 'pending')
            ->when($validated['provider'] ?? null, fn ($query, string $provider) => $query->where('provider', $provider))
            ->latest('occurred_at')
            ->limit(100)
            ->get();

        return EmailCandidateResource::collection($candidates);
    }

    public function update(
        Request $request,
        FinancialTransactionCandidate $candidate,
        EmailClassificationLearningService $learning,
    ): JsonResource {
        abort_unless($candidate->user_id === $request->user()->id, 404);
        $validated = $request->validate([
            'action' => ['required', 'string', Rule::in(['categorize', 'dismiss'])],
            'category' => ['nullable', 'required_if:action,categorize', 'string', 'max:100'],
            'learn' => ['sometimes', 'boolean'],
        ]);

        DB::transaction(function () use ($candidate, $validated, $learning): void {
            $candidate->update([
                'status' => $validated['action'] === 'dismiss' ? 'dismissed' : 'classified',
                'category_suggestion' => $validated['action'] === 'categorize'
                    ? trim($validated['category'])
                    : $candidate->category_suggestion,
            ]);
            if ($validated['learn'] ?? true) {
                $learning->learn($candidate, $validated['action'], $validated['category'] ?? null);
            }
        });

        return EmailCandidateResource::make($candidate->refresh()->load('message:id,subject,sender_email'));
    }
}

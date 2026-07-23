<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\V1\EmailCandidateResource;
use App\Models\EmailCategorizationRule;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class EmailCandidateController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $candidates = $request->user()->emailCandidates()->latest('occurred_at')->limit(200)->get();

        return response()->json(['data' => EmailCandidateResource::collection($candidates)->resolve()]);
    }

    public function update(Request $request, string $candidate): JsonResponse|EmailCandidateResource
    {
        $validated = $request->validate([
            'action' => ['required', 'in:categorize,dismiss'],
            'category' => ['required_if:action,categorize', 'nullable', 'string', 'max:120'],
            'learn' => ['sometimes', 'boolean'],
        ]);
        $record = $request->user()->emailCandidates()->find($candidate);
        if (! $record) {
            return response()->json(['message' => 'El candidato de correo no existe.', 'code' => 'email_candidate_not_found'], 404);
        }
        $record->update([
            'status' => $validated['action'] === 'categorize' ? 'categorized' : 'dismissed',
            'category' => $validated['action'] === 'categorize' ? $validated['category'] : null,
        ]);
        if (($validated['learn'] ?? false) && $record->merchant && $record->category) {
            EmailCategorizationRule::query()->updateOrCreate(
                ['user_id' => $request->user()->id, 'merchant' => $record->merchant],
                ['category' => $record->category]
            );
        }

        return new EmailCandidateResource($record);
    }
}

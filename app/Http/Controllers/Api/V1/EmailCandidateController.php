<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\V1\EmailCandidateResource;
use App\Models\EmailCategorizationRule;
use App\Models\EmailMailbox;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class EmailCandidateController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $candidates = $request->user()->emailCandidates()
            ->where('status', 'pending')
            ->latest('occurred_at')
            ->with('message')
            ->limit(200)
            ->get();

        return response()->json(['data' => EmailCandidateResource::collection($candidates)->resolve()]);
    }

    public function update(Request $request, string $candidate): JsonResponse|EmailCandidateResource
    {
        $validated = $request->validate([
            'action' => ['required', 'in:categorize,dismiss,duplicate'],
            'category' => ['required_if:action,categorize', 'nullable', 'string', 'max:120'],
            'learn' => ['sometimes', 'boolean'],
            // Cuál es el candidato bueno. Opcional: marcar como duplicado sin señalar
            // el original sigue siendo útil para quitarlo de la lista.
            'duplicate_of_id' => ['sometimes', 'nullable', 'uuid'],
        ]);
        $record = $request->user()->emailCandidates()->with('message.connection')->find($candidate);
        if (! $record) {
            return response()->json(['message' => 'El candidato de correo no existe.', 'code' => 'email_candidate_not_found'], 404);
        }

        $keptId = null;
        if ($validated['action'] === 'duplicate' && ! empty($validated['duplicate_of_id'])) {
            // El original tiene que ser del mismo usuario y no puede ser él mismo.
            if ($validated['duplicate_of_id'] === $record->id) {
                return response()->json([
                    'message' => 'Un candidato no puede ser duplicado de sí mismo.',
                    'code' => 'email_candidate_self_duplicate',
                ], 422);
            }
            $kept = $request->user()->emailCandidates()->find($validated['duplicate_of_id']);
            if (! $kept) {
                return response()->json([
                    'message' => 'El candidato original no existe.',
                    'code' => 'email_candidate_original_not_found',
                ], 404);
            }
            $keptId = $kept->id;
        }

        $status = match ($validated['action']) {
            'categorize' => 'categorized',
            'duplicate' => 'duplicate',
            default => 'dismissed',
        };
        $category = $validated['action'] === 'categorize' ? $validated['category'] : null;
        DB::transaction(function () use ($request, $record, $validated, $keptId, $status, $category): void {
            $record->update([
                'status' => $status,
                'category' => $category,
                'duplicate_of_id' => $validated['action'] === 'duplicate' ? $keptId : null,
            ]);

            $connection = $record->message?->connection;
            if ($connection) {
                $mailbox = EmailMailbox::forConnection($connection);
                $mailbox->decisions()->updateOrCreate(
                    ['provider_message_id' => $record->message->provider_message_id],
                    ['status' => $status, 'category' => $category, 'decided_at' => now()]
                );
            }

            if (($validated['learn'] ?? false) && $status === 'categorized' && $record->merchant && $category) {
                EmailCategorizationRule::query()->updateOrCreate(
                    ['user_id' => $request->user()->id, 'merchant' => $record->merchant],
                    ['category' => $category]
                );
            }
        });

        return new EmailCandidateResource($record);
    }
}

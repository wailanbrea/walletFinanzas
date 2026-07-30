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
            'action' => ['required', 'in:categorize,dismiss,duplicate'],
            'category' => ['required_if:action,categorize', 'nullable', 'string', 'max:120'],
            'learn' => ['sometimes', 'boolean'],
            // Cuál es el candidato bueno. Opcional: marcar como duplicado sin señalar
            // el original sigue siendo útil para quitarlo de la lista.
            'duplicate_of_id' => ['sometimes', 'nullable', 'uuid'],
        ]);
        $record = $request->user()->emailCandidates()->find($candidate);
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

        $record->update([
            'status' => match ($validated['action']) {
                'categorize' => 'categorized',
                'duplicate' => 'duplicate',
                default => 'dismissed',
            },
            'category' => $validated['action'] === 'categorize' ? $validated['category'] : null,
            'duplicate_of_id' => $validated['action'] === 'duplicate' ? $keptId : null,
        ]);
        // Un duplicado no enseña nada al clasificador: el remitente sí manda avisos de
        // movimientos reales, solo que ese cargo ya llegó por otro buzón. Aprender de
        // aquí envenenaría las detecciones futuras.
        if (($validated['learn'] ?? false) && $validated['action'] === 'categorize' && $record->merchant && $record->category) {
            EmailCategorizationRule::query()->updateOrCreate(
                ['user_id' => $request->user()->id, 'merchant' => $record->merchant],
                ['category' => $record->category]
            );
        }

        return new EmailCandidateResource($record);
    }
}

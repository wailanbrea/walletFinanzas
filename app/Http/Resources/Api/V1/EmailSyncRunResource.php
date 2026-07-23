<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class EmailSyncRunResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'sync_run_id' => $this->id,
            'status' => $this->status,
            'messages_discovered' => $this->messages_discovered,
            'messages_created' => $this->messages_created,
            'candidates_created' => $this->candidates_created,
            'conversions_backfilled' => $this->conversions_backfilled,
            'error_code' => $this->error_code,
        ];
    }
}

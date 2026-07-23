<?php

return [
    'state_ttl_minutes' => 10,
    'max_messages_per_run' => (int) env('EMAIL_SYNC_MAX_MESSAGES', 100),
];

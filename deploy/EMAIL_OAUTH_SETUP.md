# Email OAuth And Sync Setup

This document is for the person implementing and deploying Gmail and Microsoft
email synchronization in the Laravel API. The Android client is already wired
to these endpoints, but the VPS currently returns `404` for them.

## Production Requirements

- API base URL: `https://apiwallet.bsolutions.dev/api/v1/`
- Laravel must run with `APP_ENV=production` and `APP_DEBUG=false`.
- The site must use HTTPS. OAuth providers reject insecure production callback URLs.
- Secrets belong only in the VPS `.env`. Never put OAuth client secrets in the
  Android app, `local.properties`, source code, or Git.
- Keep `QUEUE_CONNECTION=database` or configure Redis, then run a permanent
  queue worker under Supervisor or systemd.

## Android API Contract

All endpoints below require `auth:sanctum` and an `Authorization: Bearer TOKEN`
header. A request without a token must return `401`, not `404`.

| Method | Path | Expected response |
|---|---|---|
| GET | `/email-connections` | `{ "data": [EmailConnection] }` |
| POST | `/email-connections/{provider}/authorization-url` | `{ "data": { "authorization_url": "https://..." } }` |
| POST | `/email-connections/{provider}/sync` | `202` and `{ "data": EmailSyncRun }` |
| GET | `/email-connections/{provider}/sync-runs/{run}` | `{ "data": EmailSyncRun }` |
| DELETE | `/email-connections/{provider}` | `204 No Content` |
| GET | `/email-candidates` | `{ "data": [EmailCandidate] }` |
| PATCH | `/email-candidates/{candidate}` | `{ "data": EmailCandidate }` |

Supported `{provider}` values are `gmail` and `microsoft`.

`EmailConnection` must contain:

```json
{
  "provider": "gmail",
  "display_name": "Gmail",
  "status": "connected",
  "email": "person@example.com",
  "configuration_ready": true,
  "connected_at": "2026-07-23T00:00:00Z",
  "expires_at": "2026-07-23T01:00:00Z"
}
```

`EmailSyncRun` must contain `sync_run_id`, `status`, `messages_discovered`,
`messages_created`, `candidates_created`, `conversions_backfilled`, and an
optional `error_code`. Valid statuses are `queued`, `running`, `completed`, and
`failed`. The Android client polls the run every 1.5 seconds for up to 45 seconds.

The candidate review request is:

```json
{ "action": "categorize", "category": "Food", "learn": true }
```

or:

```json
{ "action": "dismiss", "learn": true }
```

## Laravel Implementation Checklist

1. Add an `EmailConnectionController`, an `EmailCandidateController`, and an
   OAuth callback controller. Register the API contract routes inside the
   existing `auth:sanctum` group in `routes/api.php`.
2. Add public provider callbacks. Recommended paths are:

   ```text
   GET /api/v1/oauth/gmail/callback
   GET /api/v1/oauth/microsoft/callback
   ```

3. The callback must validate the signed OAuth state, save the connection, and
   redirect the device back to:

   ```text
   walletfinanzas://email-oauth?provider=gmail&status=connected
   walletfinanzas://email-oauth?provider=microsoft&status=connected
   ```

   On failure, redirect with `status=failed`. Do not put provider error details,
   tokens, or email addresses in the deep-link query string.
4. Create database tables for connections, OAuth state, sync runs, parsed
   candidates, and categorization rules. Every row must be scoped to the
   authenticated user.
5. Store refresh tokens encrypted at rest. Never return access or refresh tokens
   through the API and never write them to application logs.
6. Queue the provider mailbox scan. The endpoint starts one run and returns
   `202`; a job updates that run until it is `completed` or `failed`.
7. Make scans idempotent. Use a provider message ID plus user ID as a unique key
   so a retry does not create duplicate candidates or transactions.
8. Read only the metadata needed to detect financial notifications. Do not
   persist full message bodies or attachments unless there is an approved privacy
   requirement.

## Google Cloud Configuration

1. Create or select a Google Cloud project.
2. Configure the OAuth consent screen. Keep it in Testing with explicit test
   users until the app is ready for Google verification.
3. Create a Web application OAuth client. The backend, not Android, owns the
   client secret.
4. Add this authorized redirect URI exactly:

   ```text
   https://apiwallet.bsolutions.dev/api/v1/oauth/gmail/callback
   ```

5. Request the smallest scope set possible. For read-only financial-notification
   detection use `https://www.googleapis.com/auth/gmail.readonly`.
6. Add these values only to the VPS `.env`:

   ```env
   GOOGLE_OAUTH_CLIENT_ID=
   GOOGLE_OAUTH_CLIENT_SECRET=
   GOOGLE_OAUTH_REDIRECT_URI=https://apiwallet.bsolutions.dev/api/v1/oauth/gmail/callback
   ```

`gmail.readonly` is a sensitive scope. Google may require verification before
the app can serve users outside the configured test-user list.

## Microsoft Entra Configuration

1. Register an application in Microsoft Entra ID.
2. Add this Web redirect URI:

   ```text
   https://apiwallet.bsolutions.dev/api/v1/oauth/microsoft/callback
   ```

3. Grant the least privileged delegated Microsoft Graph permission needed to
   read mail, normally `Mail.Read`, plus `offline_access` for refresh tokens.
4. Store these VPS-only values:

   ```env
   MICROSOFT_OAUTH_CLIENT_ID=
   MICROSOFT_OAUTH_CLIENT_SECRET=
   MICROSOFT_OAUTH_TENANT_ID=common
   MICROSOFT_OAUTH_REDIRECT_URI=https://apiwallet.bsolutions.dev/api/v1/oauth/microsoft/callback
   ```

## VPS Deployment

1. Set production values in `.env`, including `APP_DEBUG=false`, database
   credentials, Google/Microsoft secrets, and queue connection.
2. Deploy the code and run:

   ```bash
   composer install --no-dev --optimize-autoloader
   php artisan migrate --force
   php artisan optimize:clear
   php artisan config:cache
   php artisan route:cache
   php artisan view:cache
   php artisan route:list --path=api
   ```

3. Start the worker with a process manager:

   ```bash
   php artisan queue:work --tries=3 --timeout=120
   ```

4. Reload PHP-FPM and Nginx/Apache after deployment.

## Acceptance Test

1. `GET /api/v1/health` returns `200`.
2. `GET /api/v1/email-connections` without a token returns `401`.
3. Sign into the Android app and open **Sincronizar correo**. It must show Gmail
   and Microsoft cards, not the generic loading error.
4. Tap **Conectar** for Gmail. The app must open the Google authorization page.
5. Complete consent with a configured test user. Google must return to the
   Android app through `walletfinanzas://email-oauth` and show the connection as
   connected.
6. Tap **Sincronizar**. The UI must report a completed run or a safe error code,
   never remain loading indefinitely.
7. Confirm that retrying the same sync does not create duplicate candidates.
8. Repeat the flow for Microsoft only after Gmail passes.

Do not test by sending real financial emails or by logging OAuth tokens. Use a
dedicated test mailbox with non-sensitive sample messages.

# Update The VPS From Git

Use this procedure on the server that serves `apiwallet.bsolutions.dev`.
The current server error pages show an XAMPP installation, so these commands use
the deployed repository path `C:\xampp\htdocs\walletFinanzas`.

## Before Updating

Open PowerShell on the server and enter the project directory:

```powershell
Set-Location "C:\xampp\htdocs\walletFinanzas"
git status --short
git fetch origin
git log --oneline HEAD..origin/main
```

If `git status --short` shows local changes, do not overwrite them. Commit or
back them up first. The production `.env` is server-only and must never be
committed or replaced by Git.

## Pull And Apply

Run these commands after the worktree is clean:

```powershell
git pull --ff-only origin main
& "C:\xampp\php\php.exe" artisan migrate --force
& "C:\xampp\php\php.exe" artisan optimize:clear
& "C:\xampp\php\php.exe" artisan config:cache
& "C:\xampp\php\php.exe" artisan route:cache
& "C:\xampp\php\php.exe" artisan view:cache
& "C:\xampp\php\php.exe" artisan route:list --path=api
```

`--ff-only` prevents Git from creating an automatic merge commit or replacing
unexpected local work.

## Required VPS Settings

Verify these values in the VPS `.env` before caching configuration:

```env
APP_ENV=production
APP_DEBUG=false
APP_URL=https://apiwallet.bsolutions.dev
QUEUE_CONNECTION=database
```

For Gmail OAuth, keep the client ID and client secret only in the VPS `.env`.
The Google authorized redirect URI is:

```text
https://apiwallet.bsolutions.dev/api/v1/oauth/gmail/callback
```

Do not place OAuth secrets in Git, the Android app, or `local.properties`.

## Restart Services

Restart Apache from the XAMPP Control Panel or the Windows service manager. If a
Laravel queue worker is configured, restart it after the deployment. For a
manually started worker:

```powershell
& "C:\xampp\php\php.exe" artisan queue:restart
```

## Verify The Deployment

Run these requests from the server or another machine:

```powershell
curl.exe -i "https://apiwallet.bsolutions.dev/api/v1/health"
curl.exe -i -H "Accept: application/json" "https://apiwallet.bsolutions.dev/api/v1/email-connections"
```

Expected results:

- Health returns `200` with `status: ok`.
- Email connections returns `401 Unauthenticated` without a Sanctum token.
- With a valid Android session, email connections returns the Gmail and Microsoft
  connection records.

The current route-only implementation reports Gmail as not configured and returns
`503 email_oauth_not_configured` when requesting an authorization URL. That is
expected until the OAuth callback and provider token exchange are deployed.

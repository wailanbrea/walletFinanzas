# VPS Deployment

Target VPS: `62.171.174.191`

Public API: `https://apiwallet.bsolutions.dev/api/v1/`

DNS must contain an `A` record from `apiwallet.bsolutions.dev` to `62.171.174.191`.

## Server setup

1. Deploy the repository to `/var/www/wallet-finanzas` and configure the web server with `deploy/apiwallet.bsolutions.dev.nginx.conf`.
2. Copy `deploy/.env.production.example` to `.env` on the VPS. Set `APP_KEY` with `php artisan key:generate --force`, database credentials, and SMTP credentials there. Do not commit `.env`.
3. Run `composer install --no-dev --optimize-autoloader` and `php artisan migrate --force`.
4. Run `php artisan config:cache`, `php artisan route:cache`, and `php artisan view:cache`.
5. Issue the TLS certificate before enabling the HTTPS virtual host. The application and Android client require HTTPS in production.
6. When queued work is enabled, run `php artisan queue:work --tries=3 --timeout=120` under systemd or Supervisor.

The Android project uses this API URL by default. A local `local.properties` can override it for development without changing versioned configuration.

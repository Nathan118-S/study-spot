# Running Study Spot with Docker

One container runs the API **and** the website (same address), next to a
PostgreSQL container. An optional Caddy container adds automatic HTTPS.

```
iPhone app / browser ──HTTPS──▶ Caddy :443 ──▶ app :8787 (API + website) ──▶ db (Postgres)
```

## Requirements

- A Linux server with Docker Engine and the Compose plugin
  (`docker compose version` should work).
- For HTTPS: a domain name whose DNS **A record** points at the server, and
  ports **80 and 443** open in the firewall.

Install Docker on Ubuntu/Debian:

```bash
curl -fsSL https://get.docker.com | sudo sh
```

```bash
sudo usermod -aG docker $USER && newgrp docker
```

## 1. Get the code

```bash
git clone https://github.com/Nathan118-S/study-spot.git && cd study-spot
```

## 2. Configure

```bash
cp .env.docker.example .env
```

Generate the three secrets and write them into `.env`:

```bash
for k in POSTGRES_PASSWORD SESSION_SECRET ENCRYPTION_KEY; do sed -i "s|^$k=.*|$k=$(openssl rand -hex 32)|" .env; done
```

Then edit `.env` (`nano .env`) and set at least:

- `DOMAIN`: your domain, e.g. `studyspot.example.com`
- `PUBLIC_URL`: `https://` plus that domain (used in emailed links)

Keep `.env` private; it holds your secrets. Back it up: losing
`ENCRYPTION_KEY` makes secrets stored in the admin panel unreadable.

## 3. Start

With automatic HTTPS (recommended; needs the domain and ports 80/443):

```bash
docker compose --profile https up -d --build
```

Without HTTPS (e.g. on a private network; the app is on port 8787):

```bash
docker compose up -d --build
```

The first build takes a few minutes. Check it's running:

```bash
docker compose ps
```

```bash
curl -s http://localhost:8787/api/health
```

Then open `https://<your domain>` (or `http://<server-ip>:8787`).

The database schema is created or updated automatically every time the app
starts.

## 4. Create your account and make it admin

Sign up on the website. Until email is set up, the 6-digit code is printed in
the logs:

```bash
docker compose logs app | grep "code is"
```

Make your account an admin (replace the email):

```bash
docker compose exec db psql -U studyspot -d studyspot -c "UPDATE users SET role='admin' WHERE email='you@example.com';"
```

You can then set up email, Google and Blackboard under **Admin → Integration
Secrets** without restarting anything.

## Point the iOS app at it

On your Mac, from the `ios/` folder:

```bash
python3 scripts/configure.py --bundle-id com.yourname.studyspot --server https://studyspot.example.com
```

The website is served from the same address, so `--web` isn't needed. iOS
requires HTTPS for anything that isn't on your local network.

## Everyday commands

View logs:

```bash
docker compose logs -f app
```

Update to the latest code (the admin panel's *Deploy* button is disabled in
Docker; update from the server instead):

```bash
git pull && docker compose --profile https up -d --build
```

Restart, or stop everything (data is kept in Docker volumes):

```bash
docker compose restart app
```

```bash
docker compose --profile https down
```

## Backups

Back up the database:

```bash
docker compose exec -T db pg_dump -U studyspot studyspot | gzip > studyspot-$(date +%F).sql.gz
```

Restore into an empty database:

```bash
gunzip -c studyspot-YYYY-MM-DD.sql.gz | docker compose exec -T db psql -U studyspot -d studyspot
```

`docker compose down -v` **deletes the database volume**. Don't use `-v`
unless you mean to.

## Already have nginx or another proxy?

Skip the `https` profile and proxy your domain to `http://127.0.0.1:8787`.
Set `APP_BIND=127.0.0.1` in `.env` so the app isn't exposed directly.
Forward all paths, including `/.well-known/` (used for passkeys).

## Google and Blackboard redirect URIs

Register these with Google Cloud / Blackboard, using your domain:

- `https://<domain>/api/auth/google/callback` (Google sign-in)
- `https://<domain>/api/connectors/google/callback` (Calendar / Classroom)
- `https://<domain>/blackboard/callback` (Blackboard)

# Running Study Spot with Docker

One container runs the API **and** the website (same address), next to a
PostgreSQL container. An optional Caddy container adds automatic HTTPS.

```
iPhone app / browser ──HTTPS──▶ Caddy :443 ──▶ app :8787 (API + website) ──▶ db (Postgres)
```

## Requirements

- A Linux server with Docker Engine and the Compose plugin
  (`docker compose version` should work).
- For HTTPS, either:
  - a **Cloudflare Tunnel** (domain on Cloudflare; no open ports needed), or
  - a domain whose DNS **A record** points at the server, with ports **80
    and 443** open (Caddy gets the certificate).

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

### Or: Cloudflare Tunnel (no open ports)

If your domain is on Cloudflare, a tunnel is the simplest option. The
`cloudflared` container connects *out* to Cloudflare, and Cloudflare serves
your domain over HTTPS. You don't need open ports, a public IP or
certificates. These steps use `studyspot.education`; substitute your own.

1. In the Cloudflare dashboard, open **Zero Trust → Networks → Tunnels →
   Create a tunnel**, choose **Cloudflared**, and name it (e.g. `studyspot`).
2. On the install screen, copy the **token**: the long string after
   `--token` in any of the shown commands. You don't need to run those
   commands; the container does it.
3. Under **Public Hostnames** (or **Routes**), add one:
   - Domain: `studyspot.education` (leave the subdomain empty)
   - Service type: **HTTP**, URL: **`app:8787`**

   Cloudflare creates the DNS record for you.
4. In `.env`, set:

   ```
   PUBLIC_URL=https://studyspot.education
   TUNNEL_TOKEN=<the token>
   APP_BIND=127.0.0.1
   ```

   `APP_BIND=127.0.0.1` keeps port 8787 closed to the outside; the tunnel
   reaches the app over Docker's internal network.
5. Start with the tunnel profile:

   ```bash
   docker compose --profile tunnel up -d --build
   ```

   Check it connected (look for `Registered tunnel connection`):

   ```bash
   docker compose logs cloudflared | tail
   ```

6. Recommended Cloudflare settings for `studyspot.education`:
   - **SSL/TLS → Edge Certificates → Always Use HTTPS**: on.
   - **Speed → Optimization → Rocket Loader**: off. It rewrites the
     website's scripts.
   - If the iOS app gets errors while the website works, check
     **Security → Bots** (Bot Fight Mode) and **Security → WAF**. They can
     challenge the app's API requests, because the app isn't a browser. Turn
     Bot Fight Mode off, or add a WAF *Skip* rule for URI path starting with
     `/api/`.

**Cloudflare Error 1000** means the domain still has an A/AAAA record
(often left over from before). Delete the A/AAAA records for the hostname
under **DNS → Records**, then re-save the tunnel's public hostname so
Cloudflare creates a proxied CNAME to `<tunnel-id>.cfargotunnel.com`.
**Error 1016** means the CNAME's target doesn't exist (often the tunnel's
name was used instead of its 36-character ID). Delete the CNAME and re-add
the public hostname on the *healthy* tunnel so Cloudflare creates it.
**502 / 1033** means DNS is fine, but the tunnel can't reach the app: check
the hostname's service URL and `curl http://localhost:8787/api/health`.

**Already running a tunnel?** Skip the `tunnel` profile, start with plain
`docker compose up -d --build`, and add a public hostname to your existing
tunnel:
- **cloudflared installed on this server:** service `http://localhost:8787`
- **cloudflared in another container:** `docker network connect
  study-spot_default <container>`, then service `http://app:8787`
- **cloudflared on another machine:** service `http://<server-ip>:8787`

Use the same `--profile tunnel` in the update and stop commands below
(instead of `--profile https`).

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

## iOS class Live Activities (optional)

To let the server start the "class starts soon" Live Activity on iPhones
even when the app is closed, add an APNs key under **Admin → Integration
Secrets → Apple Push**: the `.p8` file's contents (paste it as is), its
key ID and your Apple team ID. No restart is needed. The server checks
every minute, and needs outbound HTTPS to `api.push.apple.com`. See
`ios/README.md` for the app side.

## Google and Blackboard redirect URIs

Register these with Google Cloud / Blackboard, using your domain:

- `https://<domain>/api/auth/google/callback` (Google sign-in)
- `https://<domain>/api/connectors/google/callback` (Calendar / Classroom)
- `https://<domain>/blackboard/callback` (Blackboard)

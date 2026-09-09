# Study Spot

A self-hosted app for tracking classes, assignments, and study sessions, with
optional sync from Google Calendar, Google Classroom, and Blackboard Learn.

The app is a standard React (Vite) frontend backed by its own Express +
PostgreSQL API — no third-party app platform required.

## Project layout

- `src/` — the frontend (React + Vite).
- `server/` — the backend API (Express + PostgreSQL), including auth, data
  storage, two-factor auth, admin tools, and the Google/Blackboard sync
  integrations.

## Prerequisites

- Node.js 18+
- A PostgreSQL database

## Setup

0. Clone the repo. It's private, so use one of:

   ```bash
   # SSH (recommended if the server already has a deploy key added to GitHub)
   git clone git@github.com:Nathan118-S/study-spot.git && cd study-spot

   # HTTPS with a personal access token (Settings -> Developer settings ->
   # Personal access tokens -> Fine-grained, with read access to this repo)
   git clone https://<GITHUB_TOKEN>@github.com/Nathan118-S/study-spot.git && cd study-spot
   ```

   Whichever you use, keep it configured on the server afterward — the
   **Admin -> Deploy** panel's `git pull` reuses this same remote/credential,
   it doesn't take a separate token.

1. Install frontend dependencies from the project root:

   ```bash
   npm install
   ```

2. Install and configure the backend:

   ```bash
   cd server
   npm install
   cp .env.example .env
   # edit .env: set DATABASE_URL, SESSION_SECRET, and any integrations you want
   npm run migrate   # creates the database tables
   ```

3. Run both halves in development (two terminals):

   ```bash
   # terminal 1
   cd server && npm run dev

   # terminal 2 (project root)
   npm run dev
   ```

   The frontend dev server proxies `/api` requests to the backend
   (`http://localhost:8787` by default — see `vite.config.js`).

## Configuration

All backend configuration lives in `server/.env` (see `server/.env.example`
for the full list). At minimum you need `DATABASE_URL` and `SESSION_SECRET`.
Everything else is optional and only needed if you want that feature:

- **Email** (OTP codes, password resets, reminders) — set `SMTP_*`. Without
  it, emails are logged to the server console instead of sent.
- **Google sign-in / Calendar / Classroom sync** — set `GOOGLE_CLIENT_ID`,
  `GOOGLE_CLIENT_SECRET`, and the two redirect URIs. Create credentials at
  the [Google Cloud console](https://console.cloud.google.com/apis/credentials).
- **Blackboard Learn sync** — set `BLACKBOARD_CLIENT_ID`,
  `BLACKBOARD_CLIENT_SECRET`, and `BLACKBOARD_REDIRECT_URI`. Requires a
  "REST API Integration" registered with your institution's Blackboard
  instance — see the [Blackboard developer portal](https://developer.blackboard.com/portal/displayApi).

## Building for production

```bash
npm run build       # builds the frontend to dist/
cd server && npm start   # runs the API
```

Serve `dist/` behind the same domain/reverse proxy as the API (or set
`VITE_API_URL` at build time to point the frontend at a different API host).

## Scheduled jobs

`server/src/server.js` runs two recurring jobs in-process via `node-cron`:
assignment due-date reminder emails and study-streak record notifications.
For a multi-instance deployment, run these as a separate worker instead of
inside every API instance.

## Integration Secrets (admin panel)

Instead of (or in addition to) `server/.env`, an admin can configure SMTP,
Google OAuth, and Blackboard OAuth credentials from **Admin → Integration
Secrets**. Each value is pasted once, encrypted at rest (AES-256-GCM, keyed
by `ENCRYPTION_KEY`), and cannot be viewed again through the app — only
cleared, so a new value can be pasted in. Set `ENCRYPTION_KEY` in
`server/.env` before using this in production (falls back to `SESSION_SECRET`
if unset, but a dedicated key is recommended so rotating one doesn't affect
the other).

## Deploying from the admin panel

**Admin → Deploy** lets an admin pull the latest commit on the current
branch, reinstall dependencies, rebuild the frontend, and restart the app,
all from the browser. It runs `git fetch` + `git reset --hard` against the
existing clone (`server/scripts/deploy.sh`), so:

- The server's git remote needs read access to the repo already configured
  (an SSH deploy key, or an HTTPS remote with a token embedded — see the
  private-repo clone command in Setup). The deploy script reuses whatever
  credentials the initial clone used; it doesn't take a token itself.
- **The server process must run under a supervisor that restarts it
  automatically**, since a successful deploy intentionally exits the
  process (`process.exit(0)`) so it comes back up on the new code. Without
  one, a successful deploy leaves the app down until you start it by hand.

  <details>
  <summary>systemd example</summary>

  ```ini
  # /etc/systemd/system/study-spot.service
  [Unit]
  Description=Study Spot API
  After=network.target postgresql.service

  [Service]
  WorkingDirectory=/opt/study-spot/server
  ExecStart=/usr/bin/node src/server.js
  Restart=always
  RestartSec=2
  EnvironmentFile=/opt/study-spot/server/.env

  [Install]
  WantedBy=multi-user.target
  ```

  ```bash
  sudo systemctl enable --now study-spot
  ```
  </details>

  <details>
  <summary>pm2 example</summary>

  ```bash
  pm2 start server/src/server.js --name study-spot --cwd server
  pm2 save
  ```
  </details>

  <details>
  <summary>Docker example</summary>

  Run the container with `restart: unless-stopped` (Compose) or
  `--restart unless-stopped` (`docker run`) — the deploy's `git reset` still
  needs the repo to be a writable, git-tracked volume inside the container,
  not baked into the image.
  </details>

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

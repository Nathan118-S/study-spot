# AGENTS.md

## Project Context

Study Spot is a plain React + Vite frontend backed by its own Express +
PostgreSQL API. Treat it as user-owned application code, keep changes
focused on the user's request, and preserve existing project conventions.

Start with `README.md` for local setup and environment variables.

## Key Files

- `src/` — frontend application source.
- `src/api/client.js` — the frontend's API client (talks to `server/`).
- `server/` — the backend: Express routes, PostgreSQL schema (`server/schema.sql`),
  auth, two-factor auth, admin tools, and the Google/Blackboard integrations.
- `vite.config.js` — Vite config, including the `@` path alias and the dev
  proxy to the backend.
- `server/.env` — local-only environment values; never commit secrets.

## Working Notes

- Run the backend with `cd server && npm run dev`, and the frontend with
  `npm run dev` from the project root. The frontend dev server proxies
  `/api` requests to the backend.
- Entity tables (classes, assignments, templates, study sessions,
  notifications) are defined in `server/schema.sql` and exposed via the
  generic CRUD router in `server/src/routes/entities.js`.
- Backend "functions" (2FA, admin tools, leaderboard, Blackboard/Google
  sync) are dispatched by name through `server/src/routes/functions.js` and
  called from the frontend via `api.functions.invoke(name, payload)`.
- Run the relevant checks from `package.json` (frontend) before finishing
  code changes.

#!/usr/bin/env bash
# Pulls the latest commit on the current branch and reinstalls/rebuilds.
# Invoked by server/src/lib/deploy.js (admin panel "Deploy" button). The
# server process exits after this finishes so its process supervisor
# (systemd/pm2/Docker) restarts it on the new code — see README.md.
set -euo pipefail

cd "$(dirname "$0")/../.."
echo "== Study Spot deploy =="
echo "Repo: $(pwd)"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
echo "Branch: $BRANCH"

echo "-- git fetch origin $BRANCH --"
git fetch origin "$BRANCH"

echo "-- git reset --hard origin/$BRANCH --"
git reset --hard "origin/$BRANCH"

echo "-- npm install (frontend) --"
npm install --no-audit --no-fund

echo "-- npm run build (frontend) --"
npm run build

echo "-- npm install (server) --"
npm install --no-audit --no-fund --prefix server

echo "== Deploy complete =="

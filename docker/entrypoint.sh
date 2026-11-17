#!/bin/sh
# Applies the (idempotent) database schema, then starts the server.
set -e
node src/migrate.js
exec node src/server.js

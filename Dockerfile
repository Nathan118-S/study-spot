# Study Spot: one image that serves the API and the website.
#   docker compose up -d --build      (see docker-compose.yml / DOCKER.md)

# ---- Build the website ----------------------------------------------------
FROM node:22-alpine AS web
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY index.html vite.config.js tailwind.config.js postcss.config.js jsconfig.json components.json ./
COPY public ./public
COPY src ./src
RUN npm run build

# ---- Server dependencies --------------------------------------------------
FROM node:22-alpine AS server-deps
WORKDIR /app/server
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund

# ---- Runtime --------------------------------------------------------------
FROM node:22-alpine
ENV NODE_ENV=production \
    PORT=8787 \
    STUDYSPOT_DOCKER=1
WORKDIR /app
COPY --from=server-deps /app/server/node_modules ./server/node_modules
COPY server ./server
COPY --from=web /app/dist ./dist
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh && chown -R node:node /app
USER node
WORKDIR /app/server
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8787/api/health >/dev/null || exit 1
ENTRYPOINT ["entrypoint.sh"]

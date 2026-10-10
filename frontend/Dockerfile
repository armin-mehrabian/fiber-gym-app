# Fiber frontend: Next.js standalone server in three small stages.

# 1) Install dependencies (cached until package*.json changes).
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# 2) Build. BACKEND_URL must be known here, because Next.js bakes the
#    /api rewrite target into the build.
FROM node:22-alpine AS build
WORKDIR /app
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=$BACKEND_URL
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# 3) Run only the standalone output: no full node_modules, no source.
FROM node:22-alpine AS run
WORKDIR /app
ARG BACKEND_URL=http://backend:8080
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
# Server components call the backend directly at runtime, so they need it too.
ENV BACKEND_URL=$BACKEND_URL
# Listen on all interfaces so other containers and the host can reach it.
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

# The standalone server doesn't include static assets; copy them next to it.
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
# Once a public/ folder exists, also add: COPY --from=build --chown=node:node /app/public ./public

USER node
EXPOSE 3000
CMD ["node", "server.js"]

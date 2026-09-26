# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Baked in at build time - Next.js inlines NEXT_PUBLIC_* vars into the client bundle. Empty means
# every API call is a relative path (e.g. /api/me/loans), which nginx then routes to the backend -
# same origin as the page, so no CORS is needed in production.
ARG NEXT_PUBLIC_API_URL=""
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

# Runtime stage: only the standalone server output, static assets, and public files - no node_modules,
# no source, no dev dependencies.
FROM node:20-alpine
WORKDIR /app
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
ENV PORT=3000
ENTRYPOINT ["node", "server.js"]

FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
ENV API_BACKEND_URL=http://api-gateway:8080
ENV NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
# API_BACKEND_URL must be set at RUNTIME (not just build time) because Next.js
# standalone rewrites() are evaluated server-side when the process starts.
# The build stage ENV is discarded when this new layer begins.
ENV API_BACKEND_URL=http://api-gateway:8080
ENV NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]

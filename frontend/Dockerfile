# ─────────────────────────────────────────────
#  Stage 1 — Build
# ─────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ─────────────────────────────────────────────
#  Stage 2 — Runtime (Nginx)
# ─────────────────────────────────────────────
FROM nginx:1.27-alpine

RUN addgroup -S notifyflow && adduser -S notifyflow -G notifyflow

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

USER notifyflow

CMD ["nginx", "-g", "daemon off;"]

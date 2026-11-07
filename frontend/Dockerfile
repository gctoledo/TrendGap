# syntax=docker/dockerfile:1
FROM node:24.22.2-alpine3.24 AS build
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY index.html tsconfig*.json vite.config.ts ./
COPY src src
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080

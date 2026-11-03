# Imagen del frontend (opcional: Render usa Static Site). Vite incrusta las variables VITE_* al compilar, por eso
# llegan como build args. Construir desde frontend/:
#   docker build --build-arg VITE_API_URL=http://localhost:8080 --build-arg VITE_SUPABASE_ANON_KEY=... -t xela-stay-web .

# --- Etapa 1: compilación -----------------------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=http://localhost:8080
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_ANON_KEY
ENV VITE_API_URL=$VITE_API_URL \
    VITE_SUPABASE_URL=$VITE_SUPABASE_URL \
    VITE_SUPABASE_ANON_KEY=$VITE_SUPABASE_ANON_KEY
RUN npm run build

# --- Etapa 2: nginx sirve los archivos estáticos ------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

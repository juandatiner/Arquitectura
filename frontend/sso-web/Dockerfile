# SSOWeb.zip · nodo SSOWeb SERVER :80 (Nginx) · Máquina 1 · 10.43.99.2 (DMZ)
#   docker buildx build --platform linux/amd64 -t sso-ssoweb frontend/sso-web
# Angular compila a HTML/JS/CSS estáticos, se empaquetan en SSOWeb.zip y Nginx los sirve tal cual.

FROM node:22-alpine AS construccion
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npx ng build && apk add --no-cache zip && cd dist/sso-web/browser && zip -qr /SSOWeb.zip .

FROM nginx:1.29-alpine
COPY --from=construccion /SSOWeb.zip /SSOWeb.zip
RUN rm -rf /usr/share/nginx/html/* && unzip -q /SSOWeb.zip -d /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/ | grep -q sso-pantalla-web-subasta || exit 1

# Despliegue en las tres máquinas

Todo se construye en la máquina de desarrollo. Cada VM solo necesita Docker Engine + Compose v2.

## 1. Preparar cada VM (una vez)

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # cerrar sesión y volver a entrar
docker compose version
```

Abrir en el firewall los puertos de la tabla de `docs/arquitectura.md`.

## 2. Construir y publicar imágenes (máquina de desarrollo)

```bash
echo $GHCR_TOKEN | docker login ghcr.io -u juandatiner --password-stdin
make images        # docker buildx --platform linux/amd64 ... --push
```

Sin registro: `make export` genera `dist/images-maquina-N.tar` y luego
`scp dist/images-maquina-N.tar vm:` + `docker load -i images-maquina-N.tar`.

## 3. Arrancar cada máquina

```bash
scp -r deploy/maquina-N usuario@IP:~/sso
ssh usuario@IP
cd ~/sso && cp .env.example .env   # revisar IP y contraseñas
docker compose pull && docker compose up -d
docker compose ps
```

Orden recomendado: **Máquina 3** (AD, OFAC, YoPago, bases) → **Máquina 2** (RabbitMQ, microservicios) → **Máquina 1** (Nginx, gateway, web).

## 4. Verificar

```bash
./scripts/smoke-test.sh   # recorre CU I → X contra la IP de Máquina 1
```

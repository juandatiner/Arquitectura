# 0004 · Docker Compose por máquina con imágenes amd64

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
Se desarrolla y construye en un Mac (arm64) y se ejecuta en tres máquinas virtuales (amd64 asumido). El enunciado pide servicios desplegados fuera del IDE y el aporte grupal exige Docker con microservicios en distintas máquinas comunicándose.

## Decisión
- Cada artefacto tiene `Dockerfile` multi-etapa (compila dentro del contenedor; la máquina no necesita Maven ni el SDK .NET).
- Se construye con `docker buildx --platform linux/amd64` y se publica en GHCR (`ghcr.io/juandatiner/sso-<servicio>`), vía GitHub Actions o desde el Mac.
- Cada máquina tiene `deploy/maquina-N/docker-compose.yml` y `.env` con las IP reales. Los servicios publican su puerto al host para llamadas entre máquinas.
- Plan B sin registro: `docker save | ssh vm docker load`.

## Consecuencias
- Despliegue reproducible: `docker compose pull && docker compose up -d` por máquina.
- Cambiar una IP solo toca `.env`.

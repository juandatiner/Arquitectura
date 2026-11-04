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
make images IMAGE_REGISTRY=ghcr.io/juandatiner IMAGE_TAG=<etiqueta>   # docker buildx --platform linux/amd64 ... --push
```

`IMAGE_REGISTRY` e `IMAGE_TAG` deben coincidir con los de `deploy/maquina-N/.env`. Para construir sin publicar: `make images-local` (mismas variables; `s=<componente>` limita a uno).

Sin registro, `make export` (mismas variables) genera `dist/images-maquina-N.tar` a partir de `deploy/maquina-N/docker-compose.yml`; si ese archivo no existe, falla sin exportar nada. Luego
`scp dist/images-maquina-N.tar vm:` + `docker load -i images-maquina-N.tar`, y en el paso 3 se omite `docker compose pull`.

## 3. Arrancar cada máquina

```bash
scp -r deploy/maquina-N usuario@IP:~/sso
ssh usuario@IP
cd ~/sso && cp .env.example .env   # revisar IP y contraseñas
docker compose pull && docker compose up -d
docker compose ps
```

Orden recomendado: **Máquina 3** (AD, OFAC, YoPago, bases) → **Máquina 2** (RabbitMQ, microservicios) → **Máquina 1** (Nginx, gateway, web).

## 4. Cargar datos (una vez, en la VM)

El código no trae datos. Con los CSV que viven en la VM (plantillas en `tools/carga/plantillas`):

```bash
# Máquina 3 (AD)
AD_CONTENEDOR=<contenedor-ad> AD_CLAVE_ADMIN=<clave> python3 -I tools/carga/cargar_usuarios.py usuarios.csv --destino ad
# Máquina 2 (DB ACCESO, DB CATALOGO, DB SUBASTA)
DB_ACCESO_CONTENEDOR=<contenedor> python3 -I tools/carga/cargar_usuarios.py usuarios.csv --destino acceso
DB_CATALOGO_CONTENEDOR=<contenedor> DB_SUBASTA_CONTENEDOR=<contenedor> python3 -I tools/carga/cargar_subastas.py subastas.csv
```

Ver `tools/carga/README.md`.

## 5. Verificar

En cada VM, `docker compose ps` debe mostrar los contenedores `healthy`. Luego, desde la máquina de desarrollo:

```bash
make smoke MAQUINA1_IP=<ip>                                       # entrada pública: SSOWeb, token, HTTPS de VII.20, YoPagoWeb
make smoke MAQUINA1_IP=<ip> SMOKE_USUARIO=<u> SMOKE_CLAVE=<c>     # además login, catálogo y mis compras
```

La prueba es de solo lectura y cubre los CU I y II y el camino hasta `WSCompra`. Los CU III a X crean datos y se prueban aparte, con los CSV cargados en el paso 4.

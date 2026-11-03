# Despliegue en las tres máquinas

Todo se construye en la máquina de desarrollo. Cada VM solo necesita Docker Engine + Compose v2.

## 1. Preparar cada VM (una vez)

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # cerrar sesión y volver a entrar
docker compose version
```

Abrir en el firewall solo estos puertos (son los únicos que publica cada `docker-compose.yml`):

| Máquina | Puerto | Quién entra |
|---------|--------|-------------|
| 1 | 80, 5000 | Navegador; móvil (80) |
| 1 | 443 | `IntegraSSO`, Máquina 3 (VII.20) |
| 2 | 8081, 8082, 8083, 8084 | `SrvRuteo`, Máquina 1 |
| 2 | 8085 | Nginx, Máquina 1 |
| 2 | 8087, 5432 (DB COMPRA) | `MSCompra` réplica 2, Máquina 3 |
| 2 | 5672 | `MSCompra` réplica 2, `MSPago`, SIN y SEN, Máquina 3 |
| 3 | 8085, 5001 | Nginx y `ProxyWSYoPagoWeb`, Máquina 1 |
| 3 | 5086 | `SrvRuteo`, Máquina 1 |
| 3 | 389, 9001, 1025, 9004 | `MSAcceso`, `IntegraOFAC` e `IntegraMail` / `IntegraSMS`, Máquina 2 |

## 2. Construir y publicar imágenes (máquina de desarrollo)

Las publica en GHCR [`release.yml`](../.github/workflows/release.yml) al subir una etiqueta `vX.Y.Z` (o a mano con `gh workflow run release.yml --ref vX.Y.Z`),
con los tags `X.Y.Z`, `X.Y` y `latest`. En el `.env` de cada VM: `IMAGE_REGISTRY=ghcr.io/juandatiner` e `IMAGE_TAG` con uno de esos tags.

Para construirlas desde la máquina de desarrollo, con `deploy/maquina-N/.env` lleno (ver paso 3). Todas las imágenes son `linux/amd64`.

```bash
echo $GHCR_TOKEN | docker login ghcr.io -u juandatiner --password-stdin
docker compose -f deploy/maquina-N/docker-compose.yml build --push
```

Sin registro, se construye sin `--push` y se copian las imágenes a la VM:

```bash
docker compose -f deploy/maquina-N/docker-compose.yml build
docker save $(docker compose -f deploy/maquina-N/docker-compose.yml config --images | sort -u) | gzip > imagenes-maquina-N.tar.gz
scp imagenes-maquina-N.tar.gz usuario@IP:
ssh usuario@IP 'gunzip -c imagenes-maquina-N.tar.gz | docker load'
```

## 3. Arrancar cada máquina

Los `docker-compose.yml` usan archivos hermanos de `deploy/` (`balanceador`, `mom`, `ad`, `sms`, `secretos`), así que se copia la carpeta
completa, pero **sin `secretos`**: cada VM recibe solo los suyos.

| Máquina | Archivos en `deploy/secretos/` | Se generan con |
|---------|--------------------------------|----------------|
| 1 | `token_publica.pem`, `balanceador.crt`, `balanceador.key` | `tools/scripts/generar-claves-token.sh`, `tools/scripts/generar-certificado-tls.sh deploy/secretos <IP-maquina-1>` |
| 2 | `token_privada.pem`, `mom/usuarios.json` | `tools/scripts/generar-claves-token.sh`, `tools/scripts/generar-usuario-mom.py` |
| 3 | `balanceador.crt` (el mismo de Máquina 1) | |

```bash
rsync -a --exclude secretos --exclude dev deploy usuario@IP:~/sso/
rsync -a --relative tools/carga usuario@IP:~/sso/
# Copiar a ~/sso/deploy/secretos/ los archivos de la tabla, con la misma estructura
ssh usuario@IP
cd ~/sso/deploy/maquina-N && cp .env.example .env   # llenar: nada trae valor
docker compose pull           # sin registro: omitir (las imágenes ya están cargadas)
docker compose up -d --no-build
docker compose ps
```

`CREDENCIAL_YOPAGO` (Máquinas 1 y 3), `DB_COMPRA_CLAVE` y `RABBITMQ_USUARIO` / `RABBITMQ_CLAVE` (Máquinas 2 y 3) deben ser iguales en ambas.

Orden recomendado: **Máquina 3** (AD, OFAC, YoPago, bases) → **Máquina 2** (RabbitMQ, microservicios) → **Máquina 1** (Nginx, gateway, web).
Las máquinas dependen entre sí y Compose no espera a otra máquina: `MSCompra` réplica 2 se reinicia hasta que DB COMPRA de Máquina 2 responde (`restart: unless-stopped`).

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

```bash
./scripts/smoke-test.sh   # recorre CU I → X contra la IP de Máquina 1
```

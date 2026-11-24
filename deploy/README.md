# Despliegue en las tres máquinas

Todo se construye en la máquina de desarrollo. Cada VM solo necesita Docker Engine + Compose v2.

## 1. Preparar cada VM (una vez)

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # cerrar sesión y volver a entrar
docker compose version
```

Abrir en el firewall los puertos de la tabla de `docs/arquitectura.md`.

`ufw` no filtra los puertos publicados por Docker. Las restricciones de Máquinas 2 y 3 se aplican en `DOCKER-USER`:

```bash
sudo MAQUINA1_IP=<ip> MAQUINA2_IP=<ip> MAQUINA3_IP=<ip> tools/scripts/firewall-maquina.sh <2|3>
```

Máquina 1 no lleva reglas: 80, 443 y 5000 son la entrada al sistema.

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

## Ver los correos (Mailpit)

La UI de Mailpit solo escucha en el loopback de Máquina 3. Desde tu equipo:

```bash
ssh -L 8025:127.0.0.1:8025 usuario@IP-MAQUINA-3
# abrir http://localhost:8025
```

## 5. Verificar

En cada VM, `docker compose ps` debe mostrar los contenedores `healthy`. Luego, desde la máquina de desarrollo:

```bash
make smoke MAQUINA1_IP=<ip>                                       # entrada pública: SSOWeb, token, HTTPS de VII.20, YoPagoWeb
make smoke MAQUINA1_IP=<ip> SMOKE_USUARIO=<u> SMOKE_CLAVE=<c>     # además login, catálogo y mis compras
```

La prueba es de solo lectura y cubre los CU I y II y el camino hasta `WSCompra`. Los CU III a X crean datos y se prueban aparte, con los CSV cargados en el paso 4.

## Prueba de extremo a extremo y escenarios de falla en las tres máquinas

Se corren desde Máquina 2, que ya tiene Docker local. Requiere `jq`, una llave SSH hacia Máquinas 1 y 3, y un archivo con los `.env` de Máquinas 2 y 3 unidos, que se borra al terminar (Máquina 1 está en el perímetro: no debe guardarlo).

```bash
ssh -fN -L 18025:localhost:8025 usuario@IP-MAQUINA-3
export SSO_ENV_FILE=<env unido> SSO_URL=http://<M1> SSO_HTTPS_URL=https://<M1> \
  MAILPIT_URL=http://127.0.0.1:18025 SMS_URL=http://<M3>:9004 \
  MOM_API_URL=http://$(docker inspect -f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" sso-maquina-2-mom-1):15672 \
  M1_DOCKER_HOST=ssh://usuario@<M1> M3_DOCKER_HOST=ssh://usuario@<M3> \
  M1_PREFIJO=sso-maquina-1- M2_PREFIJO=sso-maquina-2- M3_PREFIJO=sso-maquina-3- M2_IP=<M2> M3_IP=<M3>
tools/scripts/e2e.sh
tools/scripts/escenarios-falla.sh
```

Los escenarios detienen y arrancan contenedores: no correrlos con usuarios conectados.

## Todo en una sola máquina (`deploy/dev`)

Los mismos servicios de las tres máquinas en un solo Compose (35 contenedores), para correr el sistema en un equipo
con Docker, `make`, `python3` y `openssl`. Se construye con los Dockerfile del repositorio; no necesita registro.

```bash
cp deploy/dev/.env.example deploy/dev/.env            # llenar: claves inventadas, TASA_IMPUESTO=0.19, URL_PAGO=http://localhost:5000
tools/scripts/generar-claves-token.sh deploy/secretos
tools/scripts/generar-certificado-tls.sh deploy/secretos 127.0.0.1
python3 -I tools/scripts/generar-usuario-mom.py <RABBITMQ_USUARIO> <RABBITMQ_CLAVE> deploy/secretos/mom   # los del .env
make up                                                # construye y levanta; make down para apagar (nunca down -v)
```

Datos, con los CSV fuera del repositorio (plantillas en `tools/carga/plantillas`):

```bash
AD_CONTENEDOR=sso-dev-ad-1 AD_CLAVE_ADMIN=<AD_ADMIN_CLAVE> DB_ACCESO_CONTENEDOR=sso-dev-db-acceso-1 \
  python3 -I tools/carga/cargar_usuarios.py usuarios.csv --destino todo
DB_CATALOGO_CONTENEDOR=sso-dev-db-catalogo-1 DB_SUBASTA_CONTENEDOR=sso-dev-db-subasta-1 \
  python3 -I tools/carga/cargar_subastas.py subastas.csv
DB_YOPAGO_CONTENEDOR=sso-dev-db-yopago-1 python3 -I tools/carga/cargar_yopago.py yopago.csv
DB_OFAC_CONTENEDOR=sso-dev-db-ofac-1 python3 -I tools/carga/cargar_ofac.py ofac.csv
SSO_URL=http://localhost YOPAGO_URL=http://localhost:5000 bash tools/scripts/smoke-test.sh
```

Páginas: SSO `http://localhost`, YoPago `http://localhost:5000`, SMS `http://localhost:9004/sms`, correos
`http://localhost:8025`. Un puerto ocupado se cambia en el `.env` con su variable `*_PUERTO_HOST`.

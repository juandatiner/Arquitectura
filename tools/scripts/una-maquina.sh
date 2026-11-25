#!/usr/bin/env bash
# Todo el sistema en una sola máquina (deploy/dev) con un comando, desde la raíz del código.
#
#   bash tools/scripts/una-maquina.sh [carpeta-con-csv]   configura, construye, levanta, carga y prueba
#   bash tools/scripts/una-maquina.sh prueba              prueba completa, casos de uso I a X (requiere jq)
#   bash tools/scripts/una-maquina.sh estado              contenedores y direcciones
#   bash tools/scripts/una-maquina.sh apagar              apaga y conserva los datos
#
# Llena solo las variables vacías de deploy/dev/.env: claves y usuario de RabbitMQ al azar, y los
# valores de esta máquina (localhost). Cambia los puertos ocupados por otros libres. La carpeta de CSV
# (fuera del repositorio) puede tener usuarios.csv, subastas.csv, yopago.csv y ofac.csv; sin ella el
# sistema queda arriba sin datos.
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
COMPOSE="docker compose -f deploy/dev/docker-compose.yml"
ENV=deploy/dev/.env

paso() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
falla() { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# En la terminal gráfica de las VM el grupo docker no está activo: se repite con sg.
if ! docker info >/dev/null 2>&1; then
  if [ -z "${SSO_CON_SG:-}" ] && getent group docker 2>/dev/null | grep -qw "$(id -un)"; then
    export SSO_CON_SG=1
    exec sg docker -c "bash $(printf '%q' "$0") $(printf '%q ' "$@")"
  fi
  falla "no hay acceso a Docker (¿está instalado y encendido? ¿el usuario está en el grupo docker?)"
fi

cd "$AQUI/../.."
[ -f deploy/dev/docker-compose.yml ] || falla "no encuentro deploy/dev/docker-compose.yml"

ocupado() {
  if command -v ss >/dev/null; then ss -ltn | grep -q ":$1 "
  else lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; fi
}
valor() { grep "^$1=" "$ENV" | cut -d= -f2-; }
puerto() { local v; v=$(valor "$1"); echo "${v:-$2}"; }

direcciones() {
  echo "  SSO        http://localhost:$(puerto BALANCEADOR_PUERTO_HOST 80)"
  echo "  YoPago     http://localhost:$(puerto YOPAGOWEB_PUERTO_HOST 5000)"
  echo "  SMS        http://localhost:$(puerto SMS_PUERTO_HOST 9004)/sms"
  echo "  Correos    http://localhost:$(puerto MAIL_UI_PUERTO_HOST 8025)"
  echo "  RabbitMQ   http://localhost:$(puerto MOM_CONSOLA_PUERTO_HOST 15672)  (usuario $(valor RABBITMQ_USUARIO), clave en $PWD/$ENV)"
}

estado() {
  local total sanos
  total=$($COMPOSE ps -q | wc -l)
  sanos=$($COMPOSE ps --format '{{.Status}}' | grep -c '(healthy)' || true)
  echo "  contenedores: $total de 35 · healthy: $sanos de 34 (el balanceador no tiene chequeo)"
}

case "${1:-}" in
  apagar)
    paso "Apagar (los datos se conservan)"
    $COMPOSE down
    exit 0 ;;
  estado)
    estado; direcciones; exit 0 ;;
  prueba)
    command -v jq >/dev/null || falla "falta jq (sudo apt install jq · brew install jq)"
    paso "Prueba completa (casos de uso I a X)"
    set -a; . "$ENV"; set +a
    bash tools/scripts/e2e.sh | grep -E '^(==|  FALLA)'
    exit 0 ;;
esac

CSV="${1:-}"
[ -z "$CSV" ] || [ -d "$CSV" ] || falla "la carpeta de CSV '$CSV' no existe"
[ -z "$CSV" ] || CSV="$(cd "$CSV" && pwd)"

paso "Configuración (.env)"
[ -f "$ENV" ] || cp deploy/dev/.env.example "$ENV"
poner() {
  if grep -q "^$1=$" "$ENV"; then
    sed -i.bak "s|^$1=$|$1=$2|" "$ENV"; echo "  llenado: $1"
  fi
}
clave() { openssl rand -hex 12; }
poner AD_ADMIN_CLAVE "$(clave)"
poner DB_CLAVE "$(clave)"
poner RABBITMQ_USUARIO "sso-$(openssl rand -hex 3)"
poner RABBITMQ_CLAVE "$(clave)"
poner CREDENCIAL_YOPAGO "$(clave)"
poner MAIL_REMITENTE no-responder@sso.local
poner TASA_IMPUESTO 0.19
poner URL_PAGO http://localhost:5000
echo "  variables llenas: $(grep -cE '^[A-Z_]+=.+' "$ENV")"

if [ -z "$($COMPOSE ps -q 2>/dev/null)" ]; then
  paso "Puertos"
  usados=""
  for par in 80:BALANCEADOR_PUERTO_HOST 443:BALANCEADOR_HTTPS_PUERTO_HOST 389:AD_PUERTO_HOST \
             5000:YOPAGOWEB_PUERTO_HOST 5672:MOM_PUERTO_HOST 8025:MAIL_UI_PUERTO_HOST \
             8080:APIGATEWAY_PUERTO_HOST 8081:MSACCESO_PUERTO_HOST 8082:MSCATALOGO_PUERTO_HOST \
             8083:MSSUBASTA_PUERTO_HOST 8084:MSSUSCRIPCION_PUERTO_HOST 9004:SMS_PUERTO_HOST \
             15672:MOM_CONSOLA_PUERTO_HOST; do
    p=${par%%:*}; v=${par#*:}
    actual=$(puerto "$v" "$p")
    ocupado "$actual" || continue
    n=$((20000 + p % 10000))
    while ocupado "$n" || [[ " $usados " == *" $n "* ]]; do n=$((n + 1)); done
    usados="$usados $n"
    sed -i.bak "/^$v=/d" "$ENV"; echo "$v=$n" >> "$ENV"
    [ "$p" = 5000 ] && sed -i.bak "s|^URL_PAGO=.*|URL_PAGO=http://localhost:$n|" "$ENV"
    echo "  ocupado $actual -> $v=$n"
  done
  echo "  listo"
fi
rm -f "$ENV.bak"

paso "Claves y certificado"
[ -f deploy/secretos/token_privada.pem ] || tools/scripts/generar-claves-token.sh deploy/secretos 2>/dev/null | tail -1
[ -f deploy/secretos/balanceador.crt ] || tools/scripts/generar-certificado-tls.sh deploy/secretos 127.0.0.1 | tail -1
if ! grep -q "\"name\": \"$(valor RABBITMQ_USUARIO)\"" deploy/secretos/mom/usuarios.json 2>/dev/null; then
  python3 -I tools/scripts/generar-usuario-mom.py "$(valor RABBITMQ_USUARIO)" "$(valor RABBITMQ_CLAVE)" \
    deploy/secretos/mom | sed 's/^/  /'
fi

if [ -z "$($COMPOSE ps -q)" ]; then
  paso "Construir y levantar (la primera vez tarda de 15 a 30 min)"
  $COMPOSE up -d --build 2>&1 | grep -E 'Built|Started|Error|error' | tail -40 || true
else
  paso "Ya estaba encendido: solo se revisa que todo esté arriba"
  $COMPOSE up -d >/dev/null 2>&1 || true
fi

paso "Esperar a que todo esté sano (máximo 10 min)"
for i in $(seq 1 60); do
  sanos=$($COMPOSE ps --format '{{.Status}}' | grep -c '(healthy)' || true)
  [ "$sanos" -ge 34 ] && break
  # un contenedor que quedó creado o detenido (dependencia lenta) se vuelve a arrancar
  [ "$($COMPOSE ps -q | wc -l)" -ge 35 ] || $COMPOSE up -d >/dev/null 2>&1 || true
  sleep 10
done
estado
[ "$sanos" -ge 34 ] || { $COMPOSE ps --format '  {{.Name}}  {{.Status}}' | grep -v healthy; falla "no todos quedaron sanos: revise con  $COMPOSE logs <servicio>"; }

if [ -n "$CSV" ]; then
  paso "Cargar datos de $CSV"
  if [ -f "$CSV/usuarios.csv" ]; then
    AD_CONTENEDOR=sso-dev-ad-1 DB_ACCESO_CONTENEDOR=sso-dev-db-acceso-1 AD_CLAVE_ADMIN="$(valor AD_ADMIN_CLAVE)" \
      python3 -I tools/carga/cargar_usuarios.py "$CSV/usuarios.csv" --destino todo | tail -1 | sed 's/^/  /'
  fi
  [ -f "$CSV/subastas.csv" ] && DB_CATALOGO_CONTENEDOR=sso-dev-db-catalogo-1 DB_SUBASTA_CONTENEDOR=sso-dev-db-subasta-1 \
    python3 -I tools/carga/cargar_subastas.py "$CSV/subastas.csv" | sed 's/^/  /'
  [ -f "$CSV/yopago.csv" ] && DB_YOPAGO_CONTENEDOR=sso-dev-db-yopago-1 \
    python3 -I tools/carga/cargar_yopago.py "$CSV/yopago.csv" | sed 's/^/  /'
  [ -f "$CSV/ofac.csv" ] && DB_OFAC_CONTENEDOR=sso-dev-db-ofac-1 \
    python3 -I tools/carga/cargar_ofac.py "$CSV/ofac.csv" | sed 's/^/  /'
fi

paso "Prueba de humo"
SSO_URL="http://localhost:$(puerto BALANCEADOR_PUERTO_HOST 80)" \
YOPAGO_URL="http://localhost:$(puerto YOPAGOWEB_PUERTO_HOST 5000)" \
  bash tools/scripts/smoke-test.sh | tail -1 | sed 's/^/  /'

paso "Listo. Abra en el navegador:"
direcciones
echo
echo "  Prueba completa:  bash tools/scripts/una-maquina.sh prueba"
echo "  Apagar:           bash tools/scripts/una-maquina.sh apagar   (nunca down -v: borra los datos)"

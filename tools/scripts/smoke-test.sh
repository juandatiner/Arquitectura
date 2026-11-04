#!/usr/bin/env bash
# Prueba de humo de un despliegue, por la entrada pública de Máquina 1. Solo lectura: no crea ofertas,
# compras ni ningún otro dato, así que se puede repetir sobre un sistema en uso.
#
# Variables:
#   MAQUINA1_IP      obligatoria, salvo que se den SSO_URL y YOPAGO_URL
#   SSO_URL          por defecto http://$MAQUINA1_IP (BALANCEADOR DE CARGAS :80)
#   YOPAGO_URL       por defecto http://$MAQUINA1_IP:5000 (YoPagoWeb SERVER :5000)
#   SMOKE_USUARIO, SMOKE_CLAVE  opcionales: un usuario ya cargado en AD. Con ellos también se comprueba
#                    el login y las consultas autenticadas; sin ellos esas comprobaciones se reportan OMITIDAS.
#
# No recorre los casos de uso III a X: esos cambian datos y dependen de los CSV cargados en la VM.
set -uo pipefail

if [ -z "${SSO_URL:-}" ] || [ -z "${YOPAGO_URL:-}" ]; then
  : "${MAQUINA1_IP:?Falta MAQUINA1_IP (o SSO_URL y YOPAGO_URL)}"
fi
SSO_URL="${SSO_URL:-http://$MAQUINA1_IP}"
YOPAGO_URL="${YOPAGO_URL:-http://$MAQUINA1_IP:5000}"
SSO_URL="${SSO_URL%/}"
YOPAGO_URL="${YOPAGO_URL%/}"

CUERPO="$(mktemp)"
trap 'rm -f "$CUERPO"' EXIT
FALLAS=0

# nombre, código esperado, texto esperado en el cuerpo (o vacío), argumentos de curl...
comprobar() {
  local nombre="$1" esperado="$2" texto="$3" codigo
  shift 3
  codigo="$(curl -sS -m 15 -o "$CUERPO" -w '%{http_code}' "$@" 2>/dev/null)" || codigo="sin respuesta"
  if [ "$codigo" = "$esperado" ] && { [ -z "$texto" ] || grep -q "$texto" "$CUERPO"; }; then
    echo "ok    $nombre ($codigo)"
  else
    echo "FALLA $nombre: se esperaba $esperado${texto:+ con \"$texto\"}, llegó $codigo"
    FALLAS=$((FALLAS + 1))
  fi
}

comprobar "Nginx sirve SSOWeb" 200 sso-pantalla-web-subasta "$SSO_URL/"
comprobar "WSGateway exige token en el catálogo" 401 "" "$SSO_URL/api/catalogo"
comprobar "Nginx rechaza el reporte de pago sin HTTPS (VII.20)" 403 "" \
  -X POST -H 'Content-Type: application/json' -d '{}' "$SSO_URL/api/pagos/reporte"
comprobar "YoPagoWeb responde" 200 UP "$YOPAGO_URL/health"

if [ -n "${SMOKE_USUARIO:-}" ] && [ -n "${SMOKE_CLAVE:-}" ]; then
  credenciales="$(SMOKE_USUARIO="$SMOKE_USUARIO" SMOKE_CLAVE="$SMOKE_CLAVE" python3 -I -c \
    'import json,os;print(json.dumps({"usuario":os.environ["SMOKE_USUARIO"],"clave":os.environ["SMOKE_CLAVE"]}))')"
  comprobar "CU I: login por Nginx → WSGateway → WSAcceso → AD" 200 token \
    -X POST -H 'Content-Type: application/json' -d "$credenciales" "$SSO_URL/api/acceso/login"
  token="$(python3 -I -c 'import json,sys
try: print(json.load(open(sys.argv[1])).get("token",""))
except ValueError: print("")' "$CUERPO")"
  if [ -n "$token" ]; then
    comprobar "CU II: catálogo autenticado → WSCatalogo" 200 contenido \
      -H "Authorization: Bearer $token" "$SSO_URL/api/catalogo"
    comprobar "Mis compras → Nginx upstream msCompra → WSCompra" 200 "" \
      -H "Authorization: Bearer $token" "$SSO_URL/api/compras"
  else
    echo "FALLA no se obtuvo token: se omiten las consultas autenticadas"
    FALLAS=$((FALLAS + 1))
  fi
else
  echo "OMITIDAS login y consultas autenticadas: definir SMOKE_USUARIO y SMOKE_CLAVE"
fi

if [ "$FALLAS" -gt 0 ]; then
  echo "✗ $FALLAS comprobaciones fallaron"
  exit 1
fi
echo "✓ Prueba de humo correcta"

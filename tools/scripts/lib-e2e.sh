#!/usr/bin/env bash
# Funciones comunes de e2e.sh y escenarios-falla.sh (no se ejecuta sola).
#
# Los datos de cada ejecución se generan en un directorio temporal fuera del repositorio y se cargan con
# tools/carga, igual que en un despliegue real. Todos los identificadores llevan la marca de la ejecución,
# así que no chocan con datos anteriores ni se borra nada al terminar.
#
# Variables (con su valor para deploy/dev; los puertos respetan <SERVICIO>_PUERTO_HOST del compose):
#   SSO_ENV_FILE     archivo .env con las credenciales              deploy/dev/.env
#   SSO_URL          balanceador HTTP (Máquina 1)                   http://localhost:80
#   SSO_HTTPS_URL    balanceador HTTPS (Máquina 1)                  https://localhost:443
#   MAILPIT_URL      UI de MAIL SERVER (Máquina 3)                  http://localhost:8025
#   SMS_URL          SMS SERVER (Máquina 3)                         http://localhost:9004
#   MOM_API_URL      consola de MOM SERVER (Máquina 2)              http://localhost:15672
#   M2_PREFIJO, M3_PREFIJO          prefijo de los contenedores de cada máquina   sso-dev-
#   M2_DOCKER_HOST, M3_DOCKER_HOST  en las tres máquinas, el DOCKER_HOST de cada una (ej. ssh://usuario@IP)

RAIZ=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SSO_ENV_FILE=${SSO_ENV_FILE:-$RAIZ/deploy/dev/.env}
[ -f "$SSO_ENV_FILE" ] || { echo "No existe $SSO_ENV_FILE (ver deploy/dev/.env.example)" >&2; exit 2; }
for herramienta in curl jq python3 docker openssl; do
    command -v "$herramienta" >/dev/null || { echo "Falta la herramienta $herramienta" >&2; exit 2; }
done
set -a
# shellcheck disable=SC1090
. "$SSO_ENV_FILE"
set +a

SSO_URL=${SSO_URL:-http://localhost:${BALANCEADOR_PUERTO_HOST:-80}}
SSO_HTTPS_URL=${SSO_HTTPS_URL:-https://localhost:${BALANCEADOR_HTTPS_PUERTO_HOST:-443}}
MAILPIT_URL=${MAILPIT_URL:-http://localhost:${MAIL_UI_PUERTO_HOST:-8025}}
SMS_URL=${SMS_URL:-http://localhost:${SMS_PUERTO_HOST:-9004}}
MOM_API_URL=${MOM_API_URL:-http://localhost:${MOM_CONSOLA_PUERTO_HOST:-15672}}
M2_PREFIJO=${M2_PREFIJO:-sso-dev-}
M3_PREFIJO=${M3_PREFIJO:-sso-dev-}

TRABAJO=$(mktemp -d "${TMPDIR:-/tmp}/sso-e2e.XXXXXX")
trap 'rm -rf "$TRABAJO"' EXIT
EJECUCION=$(date +%s)
CLAVE=$(openssl rand -hex 12)
INICIO=$SECONDS
FALLOS=0
PASOS=0

# ---------- Resultados ----------
seccion() { printf '\n== %s ==\n' "$*"; }
ok() { PASOS=$((PASOS + 1)); printf '  OK    [%3ss] %s\n' "$((SECONDS - INICIO))" "$*"; }
fallo() { PASOS=$((PASOS + 1)); FALLOS=$((FALLOS + 1)); printf '  FALLO [%3ss] %s\n' "$((SECONDS - INICIO))" "$*"; }
nota() { printf '        %s\n' "$*"; }
igual() { # igual <descripción> <esperado> <obtenido>
    if [ "$2" = "$3" ]; then ok "$1 = $3"; else fallo "$1: se esperaba '$2', se obtuvo '$3'"; fi
}
esperar() { # esperar <descripción> <segundos> <comando...>: reintenta hasta que el comando termine bien
    local descripcion=$1 limite=$2 desde=$SECONDS
    shift 2
    while [ $((SECONDS - desde)) -lt "$limite" ]; do
        if "$@"; then
            ok "$descripcion (en $((SECONDS - desde))s)"
            return 0
        fi
        sleep 2
    done
    fallo "$descripcion: no ocurrió en ${limite}s"
    return 1
}
resumen() {
    seccion "Resultado: $((PASOS - FALLOS)) de $PASOS comprobaciones bien, $FALLOS fallos, $((SECONDS - INICIO))s"
    [ "$FALLOS" -eq 0 ]
}

# ---------- HTTP ----------
# pedir <método> <url> [token] [json]: deja el resultado en CODIGO y CUERPO. -k: certificado autofirmado de Nginx.
pedir() {
    local argumentos=(-sS -k -m 20 -o "$TRABAJO/respuesta" -w '%{http_code}' -X "$1" "$2")
    [ -n "${3:-}" ] && argumentos+=(-H "Authorization: Bearer $3")
    [ -n "${4:-}" ] && argumentos+=(-H 'Content-Type: application/json' -d "$4")
    : >"$TRABAJO/respuesta"
    CODIGO=$(curl "${argumentos[@]}" 2>/dev/null) || CODIGO=000
    CUERPO=$(cat "$TRABAJO/respuesta")
}
codigo_es() { # codigo_es <descripción> <código>: comprueba la última petición
    if [ "$CODIGO" = "$2" ]; then ok "$1 → HTTP $CODIGO"; else fallo "$1: se esperaba HTTP $2, se obtuvo $CODIGO $CUERPO"; fi
}

# ---------- Docker ----------
en_maquina_3() { case "$1" in ad | db-ofac | db-sin | db-sen | db-pago | db-yopago) return 0 ;; *) return 1 ;; esac; }
contenedor() { if en_maquina_3 "$1"; then echo "${M3_PREFIJO}$1-1"; else echo "${M2_PREFIJO}$1-1"; fi; } # contenedor <servicio>
docker_de() { # docker_de <servicio> <argumentos de docker...>: en la máquina del servicio
    local servicio=$1 destino=${M2_DOCKER_HOST:-}
    shift
    if en_maquina_3 "$servicio"; then destino=${M3_DOCKER_HOST:-}; fi
    if [ -n "$destino" ]; then DOCKER_HOST=$destino docker "$@"; else docker "$@"; fi
}
sql() { # sql <base> <consulta>: cada base tiene un usuario con su mismo nombre
    docker_de "db-$1" exec "$(contenedor "db-$1")" psql -U "$1" -d "$1" -Atc "$2" 2>/dev/null
}
sql_es() { [ "$(sql "$1" "$2")" = "$3" ]; } # sql_es <base> <consulta> <valor>

# ---------- Datos de la ejecución ----------
# Cada cliente se identifica con un alias de dos dígitos; lo demás se deriva de la marca de la ejecución.
usuario_de() { echo "e2e${EJECUCION}c$1"; }
cedula_de() { echo "7${EJECUCION: -8}$1"; }
correo_de() { echo "e2e-${EJECUCION}-$1@sso-pruebas.example"; }
celular_de() { echo "+573${EJECUCION: -8}$1"; }
subasta_de() { echo "E2E${EJECUCION}-$1"; }
bien_de() { echo "BIEN${EJECUCION}-$1"; }
fecha_en() { python3 -I -c 'import sys,datetime as d;print((d.datetime.now(d.timezone.utc)+d.timedelta(seconds=int(sys.argv[1]))).isoformat(timespec="seconds"))' "$1"; }
CATEGORIA=PRUEBA$(echo "$EJECUCION" | tr 0-9 A-J)

nuevo_lote() {
    cp "$RAIZ/tools/carga/plantillas/usuarios.csv" "$TRABAJO/usuarios.csv"
    cp "$RAIZ/tools/carga/plantillas/yopago.csv" "$TRABAJO/yopago.csv"
    cp "$RAIZ/tools/carga/plantillas/ofac.csv" "$TRABAJO/ofac.csv"
    cp "$RAIZ/tools/carga/plantillas/subastas.csv" "$TRABAJO/subastas.csv"
    # Las plantillas pueden no terminar en salto de línea
    for archivo in usuarios yopago ofac subastas; do
        [ -z "$(tail -c1 "$TRABAJO/$archivo.csv")" ] || echo >>"$TRABAJO/$archivo.csv"
    done
}
agregar_cliente() { # agregar_cliente <alias> [CLIENTE|ADMIN]
    echo "$(usuario_de "$1"),$(cedula_de "$1"),Cliente Prueba $1,$(celular_de "$1"),$(correo_de "$1"),Calle $1 # 10-20 Bogota,${2:-CLIENTE},$CLAVE" >>"$TRABAJO/usuarios.csv"
    echo "$(cedula_de "$1"),Cliente Prueba $1,$CLAVE" >>"$TRABAJO/yopago.csv"
}
agregar_ofac() { echo "$(cedula_de "$1"),$2,prueba e2e" >>"$TRABAJO/ofac.csv"; } # agregar_ofac <alias> <estado>
agregar_subasta() { # agregar_subasta <n> <valor mínimo> <segundos hasta el cierre>
    echo "$(subasta_de "$1"),$(bien_de "$1"),Bien de prueba $EJECUCION $1,$CATEGORIA,$2,$(fecha_en -60),$(fecha_en "$3")" >>"$TRABAJO/subastas.csv"
}
cargar_lote() {
    local carga=$RAIZ/tools/carga
    local m3=${M3_DOCKER_HOST:-} m2=${M2_DOCKER_HOST:-}
    env ${m3:+DOCKER_HOST=$m3} AD_CONTENEDOR="$(contenedor ad)" AD_CLAVE_ADMIN="$AD_ADMIN_CLAVE" \
        python3 -I "$carga/cargar_usuarios.py" "$TRABAJO/usuarios.csv" --destino ad >/dev/null &&
        env ${m2:+DOCKER_HOST=$m2} DB_ACCESO_CONTENEDOR="$(contenedor db-acceso)" \
            python3 -I "$carga/cargar_usuarios.py" "$TRABAJO/usuarios.csv" --destino acceso >/dev/null &&
        env ${m2:+DOCKER_HOST=$m2} DB_CATALOGO_CONTENEDOR="$(contenedor db-catalogo)" DB_SUBASTA_CONTENEDOR="$(contenedor db-subasta)" \
            python3 -I "$carga/cargar_subastas.py" "$TRABAJO/subastas.csv" >/dev/null &&
        env ${m3:+DOCKER_HOST=$m3} DB_YOPAGO_CONTENEDOR="$(contenedor db-yopago)" \
            python3 -I "$carga/cargar_yopago.py" "$TRABAJO/yopago.csv" >/dev/null &&
        env ${m3:+DOCKER_HOST=$m3} DB_OFAC_CONTENEDOR="$(contenedor db-ofac)" \
            python3 -I "$carga/cargar_ofac.py" "$TRABAJO/ofac.csv" >/dev/null
}

# ---------- Acciones del cliente, siempre por HTTP contra el balanceador ----------
iniciar_sesion() { # iniciar_sesion <alias>: guarda el token
    pedir POST "$SSO_URL/api/acceso/login" "" "$(jq -nc --arg u "$(usuario_de "$1")" --arg c "$CLAVE" '{usuario:$u,clave:$c}')"
    if [ "$CODIGO" = 200 ]; then echo "$CUERPO" | jq -r .token >"$TRABAJO/token-$1"; else fallo "login de $1: HTTP $CODIGO $CUERPO"; return 1; fi
}
token_de() { cat "$TRABAJO/token-$1"; }
publicar() { pedir POST "$SSO_URL/api/catalogo/publicar" "$(token_de "$1")" "$(jq -nc --arg i "$(subasta_de "$2")" '{idSubasta:$i}')"; } # publicar <alias admin> <n>
ofertar() { pedir POST "$SSO_URL/api/subastas/$(subasta_de "$2")/ofertas" "$(token_de "$1")" "{\"valor\":$3}"; } # ofertar <alias> <n> <valor>
oferta_aceptada() { ofertar "$@"; [ "$CODIGO" = 202 ]; }
compra_de() { # compra_de <alias> <n>: JSON de la compra de esa subasta
    pedir GET "$SSO_URL/api/compras" "$(token_de "$1")"
    [ "$CODIGO" = 200 ] && echo "$CUERPO" | jq -c --arg s "$(subasta_de "$2")" '[.[] | select(.idSubasta == $s)][0] // empty'
}
campo_compra() { compra_de "$1" "$2" | jq -r ".$3"; } # campo_compra <alias> <n> <campo>
estado_compra_es() { [ "$(campo_compra "$1" "$2" estado)" = "$3" ]; } # estado_compra_es <alias> <n> <estado>
iniciar_compra() { pedir POST "$SSO_URL/api/compras/$(campo_compra "$1" "$2" numeroCompra)/iniciar" "$(token_de "$1")"; } # iniciar_compra <alias> <n>
en_catalogo() { # en_catalogo <alias> <n>: la subasta sale en el catálogo
    pedir GET "$SSO_URL/api/catalogo?categoria=$CATEGORIA&tamano=50" "$(token_de "$1")"
    [ "$CODIGO" = 200 ] && echo "$CUERPO" | jq -e --arg s "$(subasta_de "$2")" 'any(.contenido[]; .idSubasta == $s)' >/dev/null
}

# ---------- Lo que observa el cliente ----------
hay_correo() { # hay_correo <alias> <texto del asunto>
    curl -sS -m 10 -G "$MAILPIT_URL/api/v1/search" --data-urlencode "query=to:$(correo_de "$1")" --data-urlencode limit=100 2>/dev/null |
        jq -e --arg t "$2" 'any(.messages[]?; .Subject | contains($t))' >/dev/null
}
cuantos_sms() { # cuantos_sms <alias> <texto>
    curl -sS -m 10 "$SMS_URL/sms" 2>/dev/null | jq --arg n "$(celular_de "$1")" --arg t "$2" '[.[] | select(.numero == $n and (.texto | contains($t)))] | length'
}
hay_sms() { [ "$(cuantos_sms "$1" "$2")" -ge 1 ] 2>/dev/null; } # hay_sms <alias> <texto>
url_pago_de() { # url_pago_de <alias>: el enlace de pago, tal como le llega al cliente por SMS
    curl -sS -m 10 "$SMS_URL/sms" | jq -r --arg n "$(celular_de "$1")" '[.[] | select(.numero == $n and (.texto | contains("Pague en YoPago")))][0].texto' | grep -oE 'https?://[^ ]+compra=[^ ]+'
}

# ---------- YoPago: el mismo recorrido del navegador por el sitio web ----------
token_formulario() { grep -oE 'name="__RequestVerificationToken"[^>]*value="[^"]+"' "$1" | head -1 | sed -E 's/.*value="([^"]+)"/\1/'; }
yopago_consultar() { # yopago_consultar <url de pago> <cédula> <clave>: deja la página en $TRABAJO/yopago.html
    local base=${1%%/\?*} compra=${1##*compra=} campo
    rm -f "$TRABAJO/cookies"
    curl -sS -m 15 -c "$TRABAJO/cookies" -b "$TRABAJO/cookies" -o "$TRABAJO/yopago-ingreso.html" "$1" || return 1
    # Como un navegador: el nombre del campo oculto con el número de compra sale del formulario, no se fija aquí
    campo=$(grep -oE '<input type="hidden" name="[A-Za-z]+"' "$TRABAJO/yopago-ingreso.html" | head -1 | sed -E 's/.*name="([^"]+)"/\1/')
    curl -sS -m 15 -c "$TRABAJO/cookies" -b "$TRABAJO/cookies" -o "$TRABAJO/yopago.html" "$base/consultar" \
        --data-urlencode "cedula=$2" --data-urlencode "clave=$3" --data-urlencode "${campo:-compra}=$compra" \
        --data-urlencode "__RequestVerificationToken=$(token_formulario "$TRABAJO/yopago-ingreso.html")"
}
yopago_pagar() { # yopago_pagar <url de pago> <número de compra>: tras yopago_consultar; deja la página en $TRABAJO/yopago.html
    local base=${1%%/\?*} token
    token=$(token_formulario "$TRABAJO/yopago.html")
    curl -sS -m 25 -c "$TRABAJO/cookies" -b "$TRABAJO/cookies" -o "$TRABAJO/yopago.html" "$base/pagar" \
        --data-urlencode "numeroCompra=$2" --data-urlencode "medio=PSE" --data-urlencode "titular=Cliente Prueba" \
        --data-urlencode "__RequestVerificationToken=$token"
}
reportar_pago() { # reportar_pago <cédula> <número de compra> <valor>: lo que hace IntegraSSO (VII.20), por HTTPS
    pedir POST "$SSO_HTTPS_URL/api/pagos/reporte" "" \
        "$(jq -nc --arg c "$1" --arg n "$2" --argjson v "$3" --arg k "$CREDENCIAL_YOPAGO" '{cedula:$c,numeroCompra:$n,valorPagado:$v,credencial:$k}')"
}

# ---------- MOM ----------
mom_api() { curl -sS -m 15 -u "$RABBITMQ_USUARIO:$RABBITMQ_CLAVE" "$@"; }
colas() { docker_de mom exec "$(contenedor mom)" rabbitmqctl -q --formatter json list_queues name messages consumers 2>/dev/null; }
mensajes_en() { colas | jq --arg c "$1" '.[] | select(.name == $c) | .messages'; } # mensajes_en <cola>
consumidores_en() { colas | jq --arg c "$1" '.[] | select(.name == $c) | .consumers'; } # consumidores_en <cola>
mensajes_son() { [ "$(mensajes_en "$1")" = "$2" ]; } # mensajes_son <cola> <cantidad>
total_dlq() { colas | jq '[.[] | select(.name | endswith(".dlq")) | .messages] | add'; }
leer_cola() { # leer_cola <cola> <cantidad> <ack_requeue_true|ack_requeue_false>
    mom_api -H 'Content-Type: application/json' -X POST "$MOM_API_URL/api/queues/%2F/$1/get" \
        -d "{\"count\":$2,\"ackmode\":\"$3\",\"encoding\":\"auto\"}"
}

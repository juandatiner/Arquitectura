#!/usr/bin/env bash
# Escenarios de falla contra deploy/dev (issue #66). Evidencia y conclusiones: docs/escenarios-falla.md.
# Detiene y arranca solo servicios del proyecto compose de desarrollo; al salir vuelve a arrancar lo que detuvo.
#
# Uso: tools/scripts/escenarios-falla.sh [ofac|rabbitmq|replica|yopago|sso|fanout|dlq ...]   (sin argumentos: todos)
set -uo pipefail
# shellcheck disable=SC1091
. "$(dirname "$0")/lib-e2e.sh"
[ -z "${M2_DOCKER_HOST:-}${M3_DOCKER_HOST:-}" ] || { echo "Estos escenarios son solo para deploy/dev" >&2; exit 2; }

DETENIDOS=""
dc() { docker compose -f "$RAIZ/deploy/dev/docker-compose.yml" --env-file "$SSO_ENV_FILE" "$@"; }
detener() {
    dc stop "$@" >/dev/null 2>&1
    DETENIDOS="$DETENIDOS $*"
    nota "detenido: $*"
}
arrancar() {
    dc start "$@" >/dev/null 2>&1
    nota "arrancado: $*"
    for servicio in "$@"; do
        DETENIDOS=$(echo "$DETENIDOS" | tr ' ' '\n' | grep -vx "$servicio" | tr '\n' ' ')
    done
}
restaurar() {
    # shellcheck disable=SC2086
    if [ -n "$(echo $DETENIDOS)" ]; then dc start $DETENIDOS >/dev/null 2>&1; fi
    rm -rf "$TRABAJO"
}
trap restaurar EXIT
trap 'exit 130' INT TERM
sano() { [ "$(docker inspect -f '{{.State.Health.Status}}' "$(contenedor "$1")" 2>/dev/null)" = healthy ]; }
marca_tiempo() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# ---------- Preparación ----------
agregar_compra() { agregar_cliente "$1"; agregar_subasta "$1" 1000000 "$2"; } # agregar_compra <alias> <segundos hasta el cierre>
cargar_y_entrar() { # cargar_y_entrar <alias...>
    agregar_cliente 90 ADMIN
    cargar_lote || { fallo "carga de datos"; exit 1; }
    for cliente in 90 "$@"; do iniciar_sesion "$cliente" || exit 1; done
}
publicar_y_ofertar() { # publicar_y_ofertar <alias>
    publicar 90 "$1"
    codigo_es "publicar subasta $1" 200
    esperar "oferta del cliente $1" 25 oferta_aceptada "$1" "$1" 1000000
}
preparar_compras() { # preparar_compras <alias...>: una compra CREADA por alias, con cierre en 30 s
    nuevo_lote
    for cliente in "$@"; do agregar_compra "$cliente" 30; done
    cargar_y_entrar "$@"
    for cliente in "$@"; do publicar_y_ofertar "$cliente"; done
    for cliente in "$@"; do esperar "compra del cliente $cliente creada" 70 estado_compra_es "$cliente" "$cliente" CREADA; done
}
llevar_a_pago() { # llevar_a_pago <alias>: compra iniciada, aprobada por la OFAC y preparada en YoPago
    iniciar_compra "$1" "$1"
    esperar "compra $1 en ESPERANDO_PAGO" 30 estado_compra_es "$1" "$1" ESPERANDO_PAGO
    esperar "SMS con el enlace de pago" 30 hay_sms "$1" "Pague en YoPago"
}
pagar_en_web() { # pagar_en_web <alias> [número de compra, si el APIGateway está apagado]
    local numero=${2:-} url
    [ -n "$numero" ] || numero=$(campo_compra "$1" "$1" numeroCompra)
    url=$(url_pago_de "$1")
    yopago_consultar "$url" "$(cedula_de "$1")" "$CLAVE" && yopago_pagar "$url" "$numero"
    if grep -q "Pago realizado" "$TRABAJO/yopago.html"; then ok "YoPago confirma el pago de $numero"; else fallo "YoPago no confirma el pago de $numero"; fi
}
terminada() { # terminada <alias>
    esperar "compra $1 TERMINADA" 60 estado_compra_es "$1" "$1" TERMINADA
    esperar "SMS de compra realizada" 20 hay_sms "$1" "fue realizada"
}

# ---------- OFAC caído ----------
intentos_ofac_al_menos() { [ "$(campo_compra "$1" "$1" intentosOfac)" -ge "$2" ] 2>/dev/null; }
escenario_ofac() {
    seccion "OFAC caído: reintentos y, al volver, la compra avanza"
    preparar_compras 13 14
    detener ofacbackend
    iniciar_compra 13 13
    codigo_es "iniciar compra con la OFAC caída (el cliente no espera)" 202
    iniciar_compra 14 14
    esperar "al menos 2 reintentos registrados" 90 intentos_ofac_al_menos 13 2
    igual "la compra sigue" EN_VALIDACION "$(campo_compra 13 13 estado)"
    nota "reintentos: $(sql compra "SELECT intentos_ofac, proximo_intento - now() FROM compra WHERE numero_compra = '$(campo_compra 13 13 numeroCompra)'")"
    nota "MSCompra: $(docker logs --since 2m "$(contenedor mscompra-replica1)" 2>&1 | grep -c 'sin respuesta') avisos 'sin respuesta' en la réplica 1, $(docker logs --since 2m "$(contenedor mscompra-replica2)" 2>&1 | grep -c 'sin respuesta') en la réplica 2"

    # Agotar los 8 intentos tarda unos 25 min: se adelanta el contador solo de la compra de esta prueba.
    sql compra "UPDATE compra SET intentos_ofac = 7, proximo_intento = now() WHERE numero_compra = '$(campo_compra 14 14 numeroCompra)'" >/dev/null
    esperar "intentos agotados: compra EN_REVISION" 30 estado_compra_es 14 14 EN_REVISION
    nota "motivo: $(campo_compra 14 14 motivo)"
    esperar "correo de demora" 20 hay_correo 14 "en revisión"
    esperar "SMS de demora" 10 hay_sms 14 "tardando más de lo normal"

    arrancar ofacbackend
    esperar "OFAC de vuelta" 90 sano ofacbackend
    esperar "la compra avanza sola a ESPERANDO_PAGO" 150 estado_compra_es 13 13 ESPERANDO_PAGO
    esperar "SMS con el enlace de pago" 30 hay_sms 13 "Pague en YoPago"
    pagar_en_web 13
    terminada 13
    igual "la compra en revisión no se mueve sola" EN_REVISION "$(campo_compra 14 14 estado)"
}

# ---------- RabbitMQ reiniciado ----------
todas_con_consumidor() { colas | jq -e '[.[] | select((.name | endswith(".dlq")) | not) | select(.consumers == 0)] | length == 0' >/dev/null; }
outbox_sin_pendientes() { sql_es "$1" "SELECT count(*) FROM outbox WHERE estado = 'PENDIENTE'" 0; }
escenario_rabbitmq() {
    seccion "RabbitMQ reiniciado: el outbox reenvía y los consumidores vuelven"
    nuevo_lote
    agregar_compra 15 30
    agregar_compra 16 240
    agregar_cliente 17
    cargar_y_entrar 15 16 17
    publicar_y_ofertar 15
    publicar_y_ofertar 16
    pedir POST "$SSO_URL/api/suscripciones" "$(token_de 17)" "$(jq -nc --arg s "$(subasta_de 16)" '{tipo:"CAMBIOS_OFERTA",valor:$s}')"
    esperar "compra del cliente 15 creada" 70 estado_compra_es 15 15 CREADA
    llevar_a_pago 15
    local numero dlq_antes
    numero=$(campo_compra 15 15 numeroCompra)
    dlq_antes=$(total_dlq)

    detener mom
    pagar_en_web 15
    if grep -q "SSO recibió su pago (APROBADO)" "$TRABAJO/yopago.html"; then ok "el pago se aprueba con RabbitMQ caído"; else fallo "el pago no se aprobó con RabbitMQ caído"; fi
    igual "PagoConfirmado queda en el outbox de MSPago (.NET)" PENDIENTE "$(sql pago "SELECT estado FROM outbox WHERE tipo = 'PagoConfirmado' AND payload->>'numeroCompra' = '$numero'")"
    ofertar 16 16 1100000
    codigo_es "oferta con RabbitMQ caído (el cliente no se entera)" 202
    igual "CambioOfertaMayor queda en el outbox de MSSubasta (Java)" PENDIENTE "$(sql subasta "SELECT estado FROM outbox WHERE tipo = 'CambioOfertaMayor' AND payload->>'idSubasta' = '$(subasta_de 16)' ORDER BY creado_en DESC LIMIT 1")"
    sleep 15
    igual "la compra espera sin perder el pago" ESPERANDO_PAGO "$(campo_compra 15 15 estado)"
    nota "intentos en el outbox de MSSubasta: $(sql subasta "SELECT intentos FROM outbox WHERE tipo = 'CambioOfertaMayor' AND payload->>'idSubasta' = '$(subasta_de 16)' ORDER BY creado_en DESC LIMIT 1")"

    arrancar mom
    esperar "RabbitMQ de vuelta" 90 sano mom
    esperar "todas las colas recuperan consumidor (Java y .NET)" 120 todas_con_consumidor
    esperar "outbox de MSPago sin pendientes" 60 outbox_sin_pendientes pago
    esperar "outbox de MSSubasta sin pendientes" 60 outbox_sin_pendientes subasta
    esperar "correo de oferta mayor, que estaba en el outbox" 30 hay_correo 17 "Nueva oferta mayor"
    terminada 15
    igual "ningún mensaje nuevo en las DLQ" "$dlq_antes" "$(total_dlq)"
}

# ---------- Réplica de MSCompra apagada ----------
upstreams_desde() { docker logs --since "$1" "$(contenedor balanceador)" 2>/dev/null | grep -E '"[A-Z]+ /compras' | grep -oE 'upstream=[^=]+ tiempo' | sed 's/ tiempo//' | sort | uniq -c; }
ambas_atienden() { # ambas_atienden <ip1> <ip2>: las dos réplicas reciben peticiones
    local desde
    desde=$(marca_tiempo)
    for _ in 1 2 3 4 5 6 7 8 9 10; do curl -sS -m 15 -o /dev/null -H "Authorization: Bearer $(token_de 18)" "$SSO_URL/api/compras"; done
    upstreams_desde "$desde" | grep -q "$1" && upstreams_desde "$desde" | grep -q "$2"
}
consumidores_son() { [ "$(consumidores_en "$1")" = "$2" ]; }
ip_de() { docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$(contenedor "$1")"; }
escenario_replica() {
    seccion "Réplica 1 de MSCompra apagada: la réplica 2 atiende HTTP y colas"
    nuevo_lote
    agregar_compra 18 30
    cargar_y_entrar 18
    publicar_y_ofertar 18
    local ip1 ip2 desde malos
    ip1=$(ip_de mscompra-replica1)
    ip2=$(ip_de mscompra-replica2)
    nota "réplica 1 = $ip1, réplica 2 = $ip2"

    # Peticiones continuas mientras se apaga la réplica: el cliente no debe ver errores
    local desde_apagado
    desde_apagado=$(marca_tiempo)
    (
        for _ in $(seq 1 40); do
            curl -sS -m 20 -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $(token_de 18)" "$SSO_URL/api/compras"
            sleep 0.5
        done >"$TRABAJO/durante-apagado"
    ) &
    local lector=$!
    sleep 3
    detener mscompra-replica1
    wait "$lector"
    malos=$(grep -vc '^200$' "$TRABAJO/durante-apagado")
    nota "códigos durante el apagado: $(sort "$TRABAJO/durante-apagado" | uniq -c | tr -s ' ' | tr '\n' ';')"
    nota "balanceador durante el apagado: $(upstreams_desde "$desde_apagado" | tr -s ' ' | tr '\n' ';')"
    igual "peticiones con error mientras se apaga la réplica (de $(wc -l <"$TRABAJO/durante-apagado" | tr -d ' '))" 0 "$malos"
    esperar "q.subastaCerrada con un solo consumidor" 30 consumidores_son q.subastaCerrada 1

    esperar "la réplica 2 crea la compra (cola q.subastaCerrada)" 70 estado_compra_es 18 18 CREADA
    desde=$(marca_tiempo)
    llevar_a_pago 18
    pagar_en_web 18
    terminada 18
    upstreams_desde "$desde" >"$TRABAJO/upstreams"
    nota "upstreams con la réplica 1 apagada: $(tr -s ' ' <"$TRABAJO/upstreams" | tr '\n' ';')"
    if grep -q "$ip2" "$TRABAJO/upstreams" && ! grep -q "$ip1" "$TRABAJO/upstreams"; then
        ok "solo la réplica 2 atendió HTTP"
    else
        fallo "se esperaba solo la réplica 2 ($ip2)"
    fi

    arrancar mscompra-replica1
    esperar "réplica 1 de vuelta" 120 sano mscompra-replica1
    esperar "q.subastaCerrada con dos consumidores" 30 consumidores_son q.subastaCerrada 2
    ip1=$(ip_de mscompra-replica1)
    esperar "el balanceador reparte de nuevo entre las dos ($ip1, $ip2)" 60 ambas_atienden "$ip1" "$ip2"
}

# ---------- YoPago caído ----------
mspago_reintenta() { [ "$(docker logs --since "$1" "$(contenedor mspago)" 2>&1 | grep -c 'Pago no preparado')" -ge "$2" ]; }
escenario_yopago() {
    seccion "YoPago caído: MSPago reintenta"
    preparar_compras 19 20
    local desde numero dlq_base
    numero=$(campo_compra 19 19 numeroCompra)
    desde=$(marca_tiempo)
    detener yopagobackend
    iniciar_compra 19 19
    esperar "compra 19 en ESPERANDO_PAGO" 30 estado_compra_es 19 19 ESPERANDO_PAGO
    esperar "MSPago registra un intento fallido" 30 mspago_reintenta "$desde" 1
    igual "sin pago preparado todavía" 0 "$(sql pago "SELECT count(*) FROM pago WHERE numero_compra = '$numero'")"
    arrancar yopagobackend
    esperar "caída corta: MSPago prepara el pago al volver YoPago" 40 sql_es pago "SELECT estado FROM pago WHERE numero_compra = '$numero'" PENDIENTE
    esperar "SMS con el enlace de pago" 30 hay_sms 19 "Pague en YoPago"
    esperar "YoPago sano" 30 curl -fsS -m 5 -o /dev/null "$(dirname "$(url_pago_de 19)")/health"
    pagar_en_web 19
    terminada 19

    seccion "YoPago caído más que los reintentos de la cola (x-delivery-limit)"
    numero=$(campo_compra 20 20 numeroCompra)
    dlq_base=$(mensajes_en q.pagoSolicitud.dlq)
    detener yopagobackend
    local inicio_caida=$SECONDS
    iniciar_compra 20 20
    esperar "compra 20 en ESPERANDO_PAGO" 30 estado_compra_es 20 20 ESPERANDO_PAGO
    esperar "la solicitud de pago cae a q.pagoSolicitud.dlq" 90 mensajes_son q.pagoSolicitud.dlq "$((dlq_base + 1))"
    nota "llegó a la DLQ $((SECONDS - inicio_caida))s después de apagar YoPago"
    arrancar yopagobackend
    sleep 25
    igual "al volver YoPago, sin reprocesar, no hay pago preparado" 0 "$(sql pago "SELECT count(*) FROM pago WHERE numero_compra = '$numero'")"
    igual "la compra queda detenida en" ESPERANDO_PAGO "$(campo_compra 20 20 estado)"
    MOM_USUARIO=$RABBITMQ_USUARIO MOM_CLAVE=$RABBITMQ_CLAVE python3 -I "$RAIZ/tools/scripts/reprocesar-dlq.py" q.pagoSolicitud 1 "$MOM_API_URL" | sed 's/^/        /'
    esperar "con reprocesar-dlq.py el pago se prepara" 30 sql_es pago "SELECT estado FROM pago WHERE numero_compra = '$numero'" PENDIENTE
    esperar "SMS con el enlace de pago" 30 hay_sms 20 "Pague en YoPago"
    pagar_en_web 20
    terminada 20
}

# ---------- SSO caído cuando YoPago reporta ----------
escenario_sso() {
    seccion "APIGateway caído al reportar el pago: YoPago reintenta (VII.19)"
    preparar_compras 21
    llevar_a_pago 21
    local numero
    numero=$(campo_compra 21 21 numeroCompra)
    detener apigateway
    pagar_en_web 21 "$numero"
    if grep -q "quedó registrado" "$TRABAJO/yopago.html"; then ok "el cliente ve su pago registrado, pendiente de informar a SSO"; else fallo "la página no avisa que el reporte quedó pendiente"; fi
    igual "reporte en DB YOPAGO" PENDIENTE "$(sql yopago "SELECT reporte FROM compra_pendiente WHERE numero_compra = '$numero'")"
    arrancar apigateway
    esperar "APIGateway de vuelta" 120 sano apigateway
    esperar "YoPago reporta solo" 60 sql_es yopago "SELECT reporte FROM compra_pendiente WHERE numero_compra = '$numero'" REPORTADO
    terminada 21
}

# ---------- Fanout de q.LiberarBienSubasta ----------
COLAS_LIBERAR="q.LiberarBienSubasta.subasta q.LiberarBienSubasta.catalogo q.LiberarBienSubasta.suscripcion"
escenario_fanout() {
    seccion "Fanout: un BienLiberado real llega a las 3 colas de liberar bien"
    igual "colas enlazadas al exchange" "q.LiberarBienSubasta.catalogo q.LiberarBienSubasta.subasta q.LiberarBienSubasta.suscripcion" \
        "$(mom_api "$MOM_API_URL/api/exchanges/%2F/q.LiberarBienSubasta/bindings/source" | jq -r '[.[].destination] | sort | join(" ")')"
    preparar_compras 11
    llevar_a_pago 11
    local numero ids
    numero=$(campo_compra 11 11 numeroCompra)
    detener mssubasta mscatalogo mssuscripcion
    esperar "pago preparado" 20 sql_es pago "SELECT estado FROM pago WHERE numero_compra = '$numero'" PENDIENTE
    reportar_pago "$(cedula_de 11)" "$numero" 1
    igual "conciliación" RECHAZADO "$(echo "$CUERPO" | jq -r .resultado)"
    esperar "compra CANCELADA" 20 estado_compra_es 11 11 CANCELADA
    for cola in $COLAS_LIBERAR; do
        esperar "$cola retiene el mensaje, sin consumidor" 20 mensajes_son "$cola" 1
    done
    ids=$(for cola in $COLAS_LIBERAR; do leer_cola "$cola" 1 ack_requeue_true | jq -r '.[0] | .properties.message_id + " " + .properties.type + " " + (.payload | fromjson | .idSubasta)'; done | sort -u)
    nota "mensajes distintos entre las 3 colas: $ids"
    igual "las 3 colas tienen el mismo mensaje" 1 "$(echo "$ids" | wc -l | tr -d ' ')"
    arrancar mssubasta mscatalogo mssuscripcion
    esperar "X.7a LIBERADA en DB SUBASTA" 120 sql_es subasta "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 11)'" LIBERADA
    esperar "X.7b LIBERADA en DB CATALOGO" 120 sql_es catalogo "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 11)'" LIBERADA
    esperar "X.7c LIBERADA en DB Suscripcion" 120 sql_es suscripcion "SELECT estado FROM subasta_conocida WHERE id_subasta = '$(subasta_de 11)'" LIBERADA
    for servicio in mssubasta mscatalogo mssuscripcion; do esperar "$servicio de vuelta" 120 sano "$servicio"; done
}

# ---------- BienLiberado inválido ----------
publicar_bien_liberado() { # publicar_bien_liberado <cuerpo>: directo al exchange, por la API de gestión
    mom_api -H 'Content-Type: application/json' -X POST "$MOM_API_URL/api/exchanges/%2F/q.LiberarBienSubasta/publish" -d \
        "$(jq -nc --arg p "$1" --arg id "$(uuidgen 2>/dev/null || echo "$EJECUCION")" '{routing_key:"",payload:$p,payload_encoding:"string",properties:{content_type:"application/json",content_encoding:"UTF-8",type:"BienLiberado",message_id:$id,delivery_mode:2}}')" | jq -c .
}
fallos_listener() { docker logs --since "$2" "$(contenedor "$1")" 2>&1 | grep -c 'Execution of Rabbit message listener failed'; }
dlq_todas_son() { # dlq_todas_son <valor por cola>
    local cola
    for cola in $COLAS_LIBERAR; do mensajes_son "$cola.dlq" "$1" || return 1; done
}
escenario_dlq() {
    seccion "BienLiberado con JSON inválido: ¿reencolado infinito o DLQ?"
    local cola base desde
    for cola in $COLAS_LIBERAR; do
        base=$(mensajes_en "$cola.dlq")
        [ "$base" = 0 ] || { fallo "$cola.dlq ya tiene $base mensajes: se omite para no tocarlos"; return; }
    done
    desde=$(marca_tiempo)
    nota "publicado: $(publicar_bien_liberado "esto no es json $EJECUCION")"
    if esperar "el mensaje llega a las 3 DLQ" 90 dlq_todas_son 1; then
        for cola in $COLAS_LIBERAR; do
            igual "$cola queda vacía" 0 "$(mensajes_en "$cola")"
            leer_cola "$cola.dlq" 1 ack_requeue_false |
                jq -r '.[0] | "        \(.routing_key): x-death=\(.properties.headers["x-death"][0] | {queue,reason,count}) payload=\(.payload)"'
        done
    else
        nota "profundidad: $(colas | jq -c '[.[] | select(.name | contains("LiberarBien"))]')"
        for cola in $COLAS_LIBERAR; do docker exec "$(contenedor mom)" rabbitmqctl -q purge_queue "$cola" >/dev/null; done
        nota "colas de liberar bien purgadas tras la prueba"
    fi
    for servicio in mssubasta mscatalogo mssuscripcion; do
        nota "$servicio: $(fallos_listener "$servicio" "$desde") entregas fallidas registradas en el log"
    done

    desde=$(marca_tiempo)
    nota "publicado: $(publicar_bien_liberado '{"sinIdSubasta":true}')"
    sleep 8
    igual "JSON válido sin idSubasta: no va a las DLQ" 0 "$(colas | jq '[.[] | select(.name | test("LiberarBien.*dlq")) | .messages] | add')"
    igual "ni se queda en las colas" 0 "$(colas | jq '[.[] | select(.name | test("LiberarBien")) | .messages] | add')"
    for servicio in mssubasta mscatalogo mssuscripcion; do
        nota "$servicio: $(fallos_listener "$servicio" "$desde") entregas fallidas registradas en el log"
    done
}

ESCENARIOS=${*:-fanout dlq ofac rabbitmq replica yopago sso}
for escenario in $ESCENARIOS; do
    "escenario_$escenario"
done
resumen

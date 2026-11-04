#!/usr/bin/env bash
# Prueba de extremo a extremo de los casos de uso I → X contra el balanceador (issue #65).
# Genera sus datos en un directorio temporal, los carga con tools/carga y comprueba solo resultados
# observables: respuestas HTTP, estados, correos en Mailpit, SMS en el simulador y filas en las bases.
# Criterio de aceptación: compra TERMINADA en menos de 2 minutos.
#
# Uso (variables en lib-e2e.sh):
#   tools/scripts/e2e.sh
# En las tres máquinas, con los mismos pasos (sin probar todavía contra las VM):
#   SSO_ENV_FILE=... SSO_URL=http://<M1> SSO_HTTPS_URL=https://<M1> MAILPIT_URL=http://<M3>:8025 SMS_URL=http://<M3>:9004 \
#   MOM_API_URL=http://<M2>:15672 M2_DOCKER_HOST=ssh://<usuario>@<M2> M3_DOCKER_HOST=ssh://<usuario>@<M3> \
#   M2_PREFIJO=sso-maquina-2- M3_PREFIJO=sso-maquina-3- tools/scripts/e2e.sh
set -uo pipefail
# shellcheck disable=SC1091
. "$(dirname "$0")/lib-e2e.sh"

# Clientes y subastas: 01 compra completa · 02 otro postor · 03 pago rechazado (CU X) · 04 OFAC REPORTADO
# · 05 OFAC EN_ESTUDIO · 90 administrador. Subasta 06 sin ofertas (desierta).
CIERRE=${SMOKE_SEGUNDOS_CIERRE:-40}
OFERTA_INICIAL=1000000
OFERTA_GANADORA=1200000

seccion "Datos de la ejecución $EJECUCION (en $TRABAJO, fuera del repositorio)"
nuevo_lote
for cliente in 01 02 03 04 05; do agregar_cliente "$cliente"; done
agregar_cliente 90 ADMIN
agregar_ofac 04 REPORTADO
agregar_ofac 05 EN_ESTUDIO
for subasta in 01 03 04 05 06; do agregar_subasta "$subasta" "$OFERTA_INICIAL" "$CIERRE"; done
agregar_subasta 07 "$OFERTA_INICIAL" 600 # cargada y nunca publicada
if cargar_lote; then ok "CSV cargados con tools/carga (usuarios, subastas, YoPago, OFAC)"; else fallo "carga de datos"; exit 1; fi
DLQ_ANTES=$(total_dlq)

seccion "CU I: autenticación"
pedir POST "$SSO_URL/api/acceso/login" "" "$(jq -nc --arg u "$(usuario_de 01)" '{usuario:$u,clave:"clave-incorrecta"}')"
codigo_es "clave incorrecta" 401
pedir GET "$SSO_URL/api/compras"
codigo_es "petición sin token" 401
for cliente in 01 02 03 04 05 90; do iniciar_sesion "$cliente" || exit 1; done
ok "login de 5 clientes y 1 administrador"

seccion "CU II: catálogo y suscripciones"
if en_catalogo 01 01; then fallo "una subasta PENDIENTE no debe salir en el catálogo"; else ok "subasta PENDIENTE no sale en el catálogo"; fi
pedir POST "$SSO_URL/api/suscripciones" "$(token_de 02)" "{\"tipo\":\"NUEVAS_SUBASTAS\",\"valor\":\"$CATEGORIA\"}"
if [ "${CODIGO#2}" != "$CODIGO" ]; then ok "suscripción a NUEVAS_SUBASTAS → HTTP $CODIGO"; else fallo "suscripción: HTTP $CODIGO $CUERPO"; fi
pedir POST "$SSO_URL/api/suscripciones" "$(token_de 03)" "{\"tipo\":\"NUEVAS_SUBASTAS\",\"valor\":\"$CATEGORIA\"}"
ID_SUSCRIPCION=$(echo "$CUERPO" | jq -r .idSuscripcion)
pedir DELETE "$SSO_URL/api/suscripciones/$ID_SUSCRIPCION" "$(token_de 03)"
codigo_es "cancelar suscripción" 200

seccion "CU III: publicar"
publicar 01 01
codigo_es "publicar sin ser administrador" 403
for subasta in 01 03 04 05 06; do
    publicar 90 "$subasta"
    codigo_es "publicar subasta $subasta" 200
done
publicar 90 01
codigo_es "publicar dos veces" 409
if en_catalogo 01 01; then ok "la subasta publicada sale en el catálogo"; else fallo "la subasta publicada no sale en el catálogo"; fi
esperar "correo de nueva subasta al suscriptor" 20 hay_correo 02 "Nueva subasta"
if hay_correo 03 "Nueva subasta"; then fallo "quien canceló la suscripción recibió correo"; else ok "quien canceló la suscripción no recibió correo"; fi
pedir POST "$SSO_URL/api/suscripciones" "$(token_de 02)" "$(jq -nc --arg s "$(subasta_de 01)" '{tipo:"CAMBIOS_OFERTA",valor:$s}')"
if [ "${CODIGO#2}" != "$CODIGO" ]; then ok "suscripción a CAMBIOS_OFERTA → HTTP $CODIGO"; else fallo "suscripción: HTTP $CODIGO $CUERPO"; fi

seccion "CU IV: ofertar"
esperar "la subasta abre y acepta la primera oferta" 25 oferta_aceptada 02 01 "$OFERTA_INICIAL"
pedir POST "$SSO_URL/api/subastas/NO-EXISTE-$EJECUCION/ofertas" "$(token_de 01)" '{"valor":1000000}'
codigo_es "oferta a una subasta inexistente" 404
ofertar 01 01 500000
codigo_es "oferta menor al mínimo" 422
igual "motivo" MENOR_AL_MINIMO "$(echo "$CUERPO" | jq -r .motivo)"
ofertar 01 01 "$OFERTA_INICIAL"
codigo_es "oferta que no supera la mayor" 422
igual "motivo" NO_SUPERA_OFERTA_MAYOR "$(echo "$CUERPO" | jq -r .motivo)"
ofertar 01 01 "$OFERTA_GANADORA"
codigo_es "oferta ganadora" 202
for pareja in "03 03" "04 04" "05 05"; do
    # shellcheck disable=SC2086
    set -- $pareja
    esperar "oferta del cliente $1" 25 oferta_aceptada "$1" "$2" "$OFERTA_GANADORA"
done
esperar "correo de oferta mayor al suscriptor" 20 hay_correo 02 "Nueva oferta mayor"
esperar "SMS de oferta mayor al suscriptor" 10 hay_sms 02 "nueva oferta mayor"

seccion "CU V: cierre de la subasta"
esperar "subasta adjudicada" "$((CIERRE + 30))" sql_es subasta "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 01)'" ADJUDICADA
esperar "subasta sin ofertas desierta" 20 sql_es subasta "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 06)'" DESIERTA
esperar "compra creada para el ganador" 20 estado_compra_es 01 01 CREADA
igual "una sola compra por subasta, con dos réplicas" 1 "$(sql compra "SELECT count(*) FROM compra WHERE id_subasta = '$(subasta_de 01)'")"
igual "el otro postor no tiene compra" "" "$(compra_de 02 01)"
igual "la subasta desierta no crea compra" 0 "$(sql compra "SELECT count(*) FROM compra WHERE id_subasta = '$(subasta_de 06)'")"
NUMERO=$(campo_compra 01 01 numeroCompra)
nota "compra $NUMERO"
esperar "correo al ganador" 20 hay_correo 01 "Ganó la subasta $(subasta_de 01)"

seccion "CU VI: iniciar compra y validar ante la OFAC"
pedir POST "$SSO_URL/api/compras/$NUMERO/iniciar" "$(token_de 02)"
codigo_es "iniciar la compra de otro cliente" 403
iniciar_compra 01 01
codigo_es "iniciar compra" 202
igual "estado devuelto" EN_VALIDACION "$(echo "$CUERPO" | jq -r .estado)"
for cliente in 03 04 05; do
    esperar "compra del cliente $cliente creada" 20 estado_compra_es "$cliente" "$cliente" CREADA
    iniciar_compra "$cliente" "$cliente"
    codigo_es "iniciar compra del cliente $cliente" 202
done
esperar "OFAC APROBADO: compra en ESPERANDO_PAGO" 30 estado_compra_es 01 01 ESPERANDO_PAGO
esperar "OFAC REPORTADO: compra CANCELADA" 30 estado_compra_es 04 04 CANCELADA
igual "motivo de la cancelación" "RECHAZADO OFAC" "$(campo_compra 04 04 motivo)"
esperar "correo de cancelación por OFAC" 20 hay_correo 04 "cancelada"
esperar "SMS de cancelación por OFAC" 10 hay_sms 04 "OFAC"

seccion "CU VII: pago por YoPago"
esperar "SMS con el valor y el enlace de pago" 30 hay_sms 01 "Pague en YoPago"
esperar "correo con el valor y el enlace de pago" 20 hay_correo 01 "Puede continuar su compra $NUMERO"
URL_PAGO_SMS=$(url_pago_de 01)
nota "enlace del SMS: $URL_PAGO_SMS"
TOTAL=$(sql pago "SELECT valor_total FROM pago WHERE numero_compra = '$NUMERO'")
igual "valor total en DB PAGO = oferta + impuestos ($TASA_IMPUESTO)" "$(python3 -I -c "print(round($OFERTA_GANADORA * (1 + $TASA_IMPUESTO), 2))")" "$(python3 -I -c "print(float('$TOTAL'))")"
yopago_consultar "$URL_PAGO_SMS" "$(cedula_de 01)" "clave-incorrecta"
if grep -q "incorrectas" "$TRABAJO/yopago.html"; then ok "YoPago rechaza una clave incorrecta"; else fallo "YoPago aceptó una clave incorrecta"; fi
yopago_consultar "$URL_PAGO_SMS" "$(cedula_de 01)" "$CLAVE"
if grep -q "Compra $NUMERO" "$TRABAJO/yopago.html"; then ok "YoPago muestra la compra pendiente"; else fallo "YoPago no muestra la compra $NUMERO"; fi
yopago_pagar "$URL_PAGO_SMS" "$NUMERO"
if grep -q "Pago realizado" "$TRABAJO/yopago.html"; then ok "YoPago confirma el pago"; else fallo "YoPago no confirma el pago"; fi
if grep -q "SSO recibió su pago (APROBADO)" "$TRABAJO/yopago.html"; then ok "SSO aprobó el reporte del pago"; else fallo "el reporte no quedó APROBADO"; fi
pedir POST "$SSO_URL/api/pagos/reporte" "" '{}'
codigo_es "reporte de pago por HTTP, sin TLS" 403
pedir POST "$SSO_HTTPS_URL/api/pagos/reporte" "" "$(jq -nc --arg c "$(cedula_de 01)" --arg n "$NUMERO" '{cedula:$c,numeroCompra:$n,valorPagado:1,credencial:"credencial-incorrecta"}')"
codigo_es "reporte con credencial incorrecta" 403
igual "pago en DB PAGO" PAGADO "$(sql pago "SELECT estado FROM pago WHERE numero_compra = '$NUMERO'")"

seccion "CU VIII: despacho con SIN y SEN"
esperar "SIN registró la salida de inventario" 30 sql_es sin "SELECT id_bien FROM salida_inventario WHERE numero_compra = '$NUMERO'" "$(bien_de 01)"
esperar "SEN programó el envío" 30 sql_es sen "SELECT direccion FROM envio WHERE numero_compra = '$NUMERO'" "Calle 01 # 10-20 Bogota"
esperar "despacho CONFIRMADO" 30 sql_es despacho "SELECT estado FROM despacho WHERE numero_compra = '$NUMERO'" CONFIRMADO
esperar "compra TERMINADA" 30 estado_compra_es 01 01 TERMINADA
TERMINADA_EN=$((SECONDS - INICIO))
if [ "$TERMINADA_EN" -lt 120 ]; then ok "compra TERMINADA en ${TERMINADA_EN}s (criterio: menos de 2 min)"; else fallo "compra TERMINADA en ${TERMINADA_EN}s (criterio: menos de 2 min)"; fi

seccion "CU IX: notificación final"
esperar "correo de compra realizada" 20 hay_correo 01 "Compra $NUMERO realizada"
esperar "SMS de compra realizada" 10 hay_sms 01 "fue realizada"
igual "un solo SMS de compra realizada" 1 "$(cuantos_sms 01 "fue realizada")"

seccion "CU X: pago rechazado y compensación"
esperar "compra del cliente 03 en ESPERANDO_PAGO" 30 estado_compra_es 03 03 ESPERANDO_PAGO
NUMERO_X=$(campo_compra 03 03 numeroCompra)
esperar "pago preparado en MSPago" 20 sql_es pago "SELECT estado FROM pago WHERE numero_compra = '$NUMERO_X'" PENDIENTE
reportar_pago "$(cedula_de 03)" "$NUMERO_X" 1
codigo_es "YoPago reporta un valor distinto" 200
igual "resultado de la conciliación" RECHAZADO "$(echo "$CUERPO" | jq -r .resultado)"
esperar "compra CANCELADA" 20 estado_compra_es 03 03 CANCELADA
igual "motivo" "pago rechazado" "$(campo_compra 03 03 motivo)"
esperar "X.7a subasta LIBERADA en DB SUBASTA" 20 sql_es subasta "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 03)'" LIBERADA
esperar "X.7b subasta LIBERADA en DB CATALOGO" 20 sql_es catalogo "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 03)'" LIBERADA
esperar "X.7c subasta LIBERADA en DB Suscripcion" 20 sql_es suscripcion "SELECT estado FROM subasta_conocida WHERE id_subasta = '$(subasta_de 03)'" LIBERADA
for base in subasta catalogo suscripcion; do
    igual "X.8 BienLiberadoConfirmado en el outbox de $base" REGISTRADO "$(sql "$base" "SELECT estado FROM outbox WHERE tipo = 'BienLiberadoConfirmado' AND payload->>'idSubasta' = '$(subasta_de 03)'")"
done
if en_catalogo 01 03; then fallo "la subasta LIBERADA sigue en el catálogo"; else ok "la subasta LIBERADA ya no sale en el catálogo"; fi
reportar_pago "$(cedula_de 03)" "$NUMERO_X" 1
igual "un reporte repetido no genera otro evento" 1 "$(sql pago "SELECT count(*) FROM outbox WHERE payload->>'numeroCompra' = '$NUMERO_X' AND tipo = 'PagoConfirmado'")"

yopago_consultar "$(url_pago_de 03)" "$(cedula_de 03)" "$CLAVE"
yopago_pagar "$(url_pago_de 03)" "$NUMERO_X"
nota "tras la compensación, YoPago dice: $(grep -oE 'SSO recibió su pago \([A-Z]+\)|Pago realizado|ya fue pagada|No hay una compra[^<]*' "$TRABAJO/yopago.html" | tr '\n' ';')"

seccion "Otros resultados"
ofertar 02 07 "$OFERTA_INICIAL"
nota "oferta a una subasta cargada y nunca publicada: HTTP $CODIGO, estado en DB CATALOGO $(sql catalogo "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 07)'"), en DB SUBASTA $(sql subasta "SELECT estado FROM subasta WHERE id_subasta = '$(subasta_de 07)'")"
INTENTOS=$(campo_compra 05 05 intentosOfac)
if [ "$(campo_compra 05 05 estado)" = EN_VALIDACION ] && [ "$INTENTOS" -ge 1 ]; then
    ok "OFAC EN_ESTUDIO: la compra sigue EN_VALIDACION con $INTENTOS reintentos"
else
    fallo "OFAC EN_ESTUDIO: $(compra_de 05 05)"
fi
igual "ningún mensaje nuevo en las DLQ" "$DLQ_ANTES" "$(total_dlq)"

resumen

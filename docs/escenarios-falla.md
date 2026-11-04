# Escenarios de falla

Se reproducen con `tools/scripts/escenarios-falla.sh [ofac|rabbitmq|replica|yopago|sso|fanout|dlq ...]` contra `deploy/dev` o las tres máquinas (`deploy/README.md`).
Las líneas de evidencia son salida real del script en `deploy/dev` (2026-11-03, Mac arm64, una sola máquina). La tabla resume la corrida en las tres VM (2026-11-04): 115 de 115 comprobaciones bien, en 672 s.

| Escenario | Resultado |
|---|---|
| OFAC caído | Pasa |
| RabbitMQ reiniciado | Pasa |
| Réplica 1 de MSCompra apagada | Pasa. En las VM, 40 de 40 respuestas 200 durante el apagado, en 4 corridas; el 504 solo se vio en `deploy/dev` |
| YoPago caído, menos de unos 30 s | Pasa |
| YoPago caído más que los reintentos (66 s en las VM) | La compra **queda detenida** hasta reprocesar la DLQ a mano |
| Fanout de `q.LiberarBienSubasta` | Pasa |
| `BienLiberado` con JSON inválido | No hay reencolado infinito: llega a la DLQ |
| APIGateway caído al reportar el pago | Pasa: YoPago guarda el reporte `PENDIENTE` y lo envía solo al volver |

## OFAC caído

La compra sigue `EN_VALIDACION` con reintentos y avanza sola cuando la OFAC vuelve.

```
detenido: ofacbackend
OK iniciar compra con la OFAC caída (el cliente no espera) → HTTP 202
OK al menos 2 reintentos registrados (en 23s)
OK la compra sigue = EN_VALIDACION
OK intentos agotados: compra EN_REVISION (en 2s)      motivo: OFAC sin respuesta tras 8 intentos
OK correo de demora (en 0s) · SMS de demora (en 0s)
arrancado: ofacbackend
OK la compra avanza sola a ESPERANDO_PAGO (en 23s)
OK compra 13 TERMINADA (en 4s)
OK la compra en revisión no se mueve sola = EN_REVISION
```

Para no esperar unos 25 min, el paso a `EN_REVISION` se forzó poniendo `intentos_ofac = 7` por SQL en la compra de esa prueba.
La espera exponencial real completa no se recorrió.

## RabbitMQ reiniciado

```
detenido: mom
OK el pago se aprueba con RabbitMQ caído
OK PagoConfirmado queda en el outbox de MSPago (.NET) = PENDIENTE
OK oferta con RabbitMQ caído (el cliente no se entera) → HTTP 202
OK CambioOfertaMayor queda en el outbox de MSSubasta (Java) = PENDIENTE
arrancado: mom
OK todas las colas recuperan consumidor (Java y .NET) (en 3s)
OK correo de oferta mayor, que estaba en el outbox (en 0s)
OK compra 15 TERMINADA (en 0s)
OK ningún mensaje nuevo en las DLQ = 0
```

## Réplica 1 de MSCompra apagada

La réplica 2 atiende HTTP y colas, y al volver la réplica 1 el balanceador reparte otra vez entre las dos.

```
detenido: mscompra-replica1
códigos durante el apagado: 39 200; 1 504;
FALLO peticiones con error mientras se apaga la réplica (de 40): se esperaba '0', se obtuvo '1'
OK la réplica 2 crea la compra (cola q.subastaCerrada) (en 4s)
OK compra 18 TERMINADA (en 4s)
upstreams con la réplica 1 apagada: 11 upstream=172.21.0.30:8085;
OK el balanceador reparte de nuevo entre las dos (172.21.0.31, 172.21.0.30) (en 5s)
```

**Defecto abierto.** Con 40 peticiones `GET /api/compras` durante el apagado, una respondió 504 en cada una de las 2 corridas.
Log de Nginx de la primera corrida:

```
22:37:49 upstream timed out while connecting to upstream (172.21.0.31:8085)
"GET /compras..." 200 upstream=172.21.0.31:8085, 172.21.0.30:8085 tiempo=2.003, 0.006
22:37:51 mscompra-replica1 could not be resolved (3: Host not found)
22:37:52 upstream timed out while connecting to upstream (172.21.0.31:8085)
"GET /compras..." 504 upstream=172.21.0.31:8085 tiempo=2.002
```

Las peticiones anteriores sí se reintentan en la réplica 2; la del 504 no.

Hipótesis sin comprobar: el DNS de Docker retira la réplica del upstream mientras esa conexión sigue abierta, y Nginx ya no reintenta.
En las tres VM el upstream usa IP, y el 504 no apareció en 4 corridas (160 peticiones), lo que es compatible con la hipótesis sin probarla.

## YoPago caído

- **Caída corta:** MSPago reintenta y prepara el pago cuando YoPago vuelve. La compra llega a `TERMINADA`.
- **Caída larga:** la solicitud agota `x-delivery-limit` y pasa a la DLQ. La compra se queda en `ESPERANDO_PAGO` y nada la reactiva (observación 24).

```
detenido: yopagobackend
OK la solicitud de pago cae a q.pagoSolicitud.dlq (en 33s)     llegó a la DLQ 35s después de apagar YoPago
arrancado: yopagobackend
OK al volver YoPago, sin reprocesar, no hay pago preparado = 0
OK la compra queda detenida en = ESPERANDO_PAGO
1 mensajes devueltos de q.pagoSolicitud.dlq a q.pagoSolicitud
OK con reprocesar-dlq.py el pago se prepara (en 2s)
OK compra 20 TERMINADA (en 3s)
```

## Fanout de `q.LiberarBienSubasta`

Con los tres consumidores apagados, un `BienLiberado` real (pago rechazado) queda en las 3 colas con el mismo `message_id`.

```
OK colas enlazadas al exchange = q.LiberarBienSubasta.catalogo q.LiberarBienSubasta.subasta q.LiberarBienSubasta.suscripcion
OK q.LiberarBienSubasta.subasta retiene el mensaje, sin consumidor (en 0s)
OK q.LiberarBienSubasta.catalogo retiene el mensaje, sin consumidor (en 1s)
OK q.LiberarBienSubasta.suscripcion retiene el mensaje, sin consumidor (en 0s)
mensajes distintos entre las 3 colas: 93ac4770-ca67-4a11-8a64-5804596ea5d7 BienLiberado E2E1793745203-11
OK X.7a LIBERADA en DB SUBASTA (en 16s) · X.7b en DB CATALOGO · X.7c en DB Suscripcion
```

## `BienLiberado` con JSON inválido

Se descarta el reencolado infinito que se sospechaba: cada consumidor falla 6 veces y el mensaje pasa a su DLQ por `delivery_limit`.

```
OK el mensaje llega a las 3 DLQ (en 2s)
q.LiberarBienSubasta.subasta.dlq: x-death={"queue":"q.LiberarBienSubasta.subasta","reason":"delivery_limit","count":1}
q.LiberarBienSubasta.catalogo.dlq: x-death={"queue":"q.LiberarBienSubasta.catalogo","reason":"delivery_limit","count":1}
q.LiberarBienSubasta.suscripcion.dlq: x-death={"queue":"q.LiberarBienSubasta.suscripcion","reason":"delivery_limit","count":1}
mssubasta: 6 entregas fallidas · mscatalogo: 6 · mssuscripcion: 6
```

Un JSON válido sin `idSubasta` (`{"sinIdSubasta":true}`) se confirma y se descarta sin error: no va a la DLQ ni deja rastro en el log.

## Sin verificar

- MSValidacion caído, como caso distinto de OFAC caído.

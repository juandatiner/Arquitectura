# MSPago

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Puente .NET del pago. Recibe la solicitud de MSCompra, calcula el valor con impuestos, registra la compra pendiente en YoPago y avisa al cliente con la URL de pago. Concilia el reporte de YoPago y publica el resultado en `q.pagoConfirmado`.

## Despliegue

Artefacto `MSPago.dll`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSPago SERVER :5086 | 5086 | MAQUINA 3 · 10.43.99.45 | Kestrel .NET 8 |

Clases del diagrama: `IntegraYoPago`, `ListenerPagoSolicitud`, `RepoOutboxPago`, `RepoPago`, `SrvPago`, `WSPagoYoPago`, `WorkerOutboxPago`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/mspago.yaml`](/docs/contratos/openapi/mspago.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/pagos/reporte` | `reportarPago` | cuerpo `ReportePago` {cedula, numeroCompra, valorPagado} | 200 `ResultadoConciliacion` | Pasos VII.24 (SrvRuteo → WSPagoYoPago reportarPago(cedula, numeroCompra, valorPagado)) y VII.25 (→ SrvPago conciliarPago). |

Salud: `GET /health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.pagoSolicitud` | `ListenerPagoSolicitud` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.notificacion` | `NotificacionPago` | `WorkerOutboxPago` |
| `q.pagoConfirmado` | `PagoConfirmado` | `WorkerOutboxPago` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_HOST` | Host de su base de datos | `Host` | `db-pago` |
| `DB_NOMBRE` | Nombre de su base de datos | `Database` | `pago` |
| `DB_USUARIO` | Usuario de su base de datos | `Username` | `pago` |
| `DB_CLAVE` | Clave de su base de datos | `Password` | `${DB_CLAVE}` |
| `TASA_IMPUESTO` | Tasa de impuestos sobre el valor ofertado | `Program.cs` | `${TASA_IMPUESTO}` |
| `RUTA_YOPAGO` | URL de WSCompraPendiente | `UrlYoPago` | `http://yopagobackend:5001` |
| `URL_PAGO` | URL de YoPagoWeb que se envía al cliente | `UrlPago` | `${URL_PAGO}` |
| `MOM_HOST` | Host de RabbitMQ | `HostName` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `UserName` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `Password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxPago` | `q.notificacion` |
| AMQP | `WorkerOutboxPago` | `q.pagoConfirmado` |
| AMQP | `q.pagoSolicitud` | `ListenerPagoSolicitud` |
| HTTP | `SrvRuteo` | `WSPagoYoPago` |
| HTTP/REST | `IntegraYoPago` | `WSCompraPendiente` |
| JDBC | `RepoOutboxPago` | `DB PAGO` |
| JDBC | `RepoPago` | `DB PAGO` |

## Construir y probar

```bash
make test-dotnet
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=mspago
```

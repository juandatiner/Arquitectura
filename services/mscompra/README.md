# MSCompra

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Orquesta la saga de compra: valida al comprador ante la OFAC, solicita el pago, solicita el despacho y compensa si algo falla (estados en `EstadoCompra`). Corre en dos réplicas detrás de Nginx (`least_conn`) que comparten DB COMPRA.

## Despliegue

Artefacto `MSCompra.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSCompra SERVER replica 1 :8085 | 8085 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |
| MSCompra SERVER replica 2 :8085 | 8085 | MAQUINA 3 · 10.43.99.45 | Tomcat embebido |

Clases del diagrama: `IntegraValidacion`, `ListenerConfDespacho`, `ListenerPago`, `ListenerSubastaCerrada`, `RepoCompra`, `RepoOutboxCompra`, `SrvCompraSaga`, `TimerValidacionOFAC`, `WSCompra`, `WorkerOutboxCompra`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/mscompra.yaml`](/docs/contratos/openapi/mscompra.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/compras/iniciar` | `iniciarCompra` | cuerpo `SolicitudInicioCompra` {numeroCompra, cliente} | 202 `CompraIniciada` | Pasos VI.8 (Nginx → WSCompra iniciarCompra(numeroCompra, cliente)) y VI.9 (→ SrvCompraSaga). |
| GET | `/compras` | `consultarCompras` | query `cedula` | 200 `List<Compra>` | VI.1 consultarMisCompras por la flecha Nginx → WSCompra (observación 6). |

Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.despachoConfirmado` | `ListenerConfDespacho` |
| `q.pagoConfirmado` | `ListenerPago` |
| `q.subastaCerrada` | `ListenerSubastaCerrada` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.LiberarBienSubasta` | `BienLiberado` | `WorkerOutboxCompra` |
| `q.despachoSolicitud` | `DespachoSolicitud` | `WorkerOutboxCompra` |
| `q.notificacion` | `NotificacionCompra` | `WorkerOutboxCompra` |
| `q.pagoSolicitud` | `PagoSolicitud` | `WorkerOutboxCompra` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-compra:5432/compra` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `compra` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |
| `RUTA_MSVALIDACION` | URL de WSValidacion | `sso.compra.url-validacion` | `http://msvalidacion:8087` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxCompra` | `q.LiberarBienSubasta` |
| AMQP | `WorkerOutboxCompra` | `q.despachoSolicitud` |
| AMQP | `WorkerOutboxCompra` | `q.notificacion` |
| AMQP | `WorkerOutboxCompra` | `q.pagoSolicitud` |
| AMQP | `q.despachoConfirmado` | `ListenerConfDespacho` |
| AMQP | `q.pagoConfirmado` | `ListenerPago` |
| AMQP | `q.subastaCerrada` | `ListenerSubastaCerrada` |
| HTTP | `Nginx` | `WSCompra` |
| HTTP/REST | `IntegraValidacion` | `WSValidacion` |
| JDBC | `RepoCompra` | `DB COMPRA` |
| JDBC | `RepoOutboxCompra` | `DB COMPRA` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl mscompra -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=mscompra
```

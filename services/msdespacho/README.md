# MSDespacho

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Coordina el despacho: solicita la salida de inventario a SIN y el envío a SEN, y confirma el despacho a MSCompra.

## Despliegue

Artefacto `MSDespacho.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSDespacho SERVER :8088 | 8088 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `ListenerConfEnvio`, `ListenerConfInventario`, `ListenerDespachoSolicitud`, `RepoDespacho`, `RepoOutboxDespacho`, `SrvDespacho`, `WorkerOutboxDespacho`.

## Endpoints

No expone API de negocio: trabaja por colas. Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.despachoSolicitud` | `ListenerDespachoSolicitud` |
| `q.envioConfirmacion` | `ListenerConfEnvio` |
| `q.inventarioConfirmacion` | `ListenerConfInventario` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.despachoConfirmado` | `DespachoConfirmado` | `WorkerOutboxDespacho` |
| `q.envioSolicitud` | `SolicitudEnvio` | `WorkerOutboxDespacho` |
| `q.inventarioSolicitud` | `SolicitudInventario` | `WorkerOutboxDespacho` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-despacho:5432/despacho` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `despacho` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxDespacho` | `q.despachoConfirmado` |
| AMQP | `WorkerOutboxDespacho` | `q.envioSolicitud` |
| AMQP | `WorkerOutboxDespacho` | `q.inventarioSolicitud` |
| AMQP | `q.despachoSolicitud` | `ListenerDespachoSolicitud` |
| AMQP | `q.envioConfirmacion` | `ListenerConfEnvio` |
| AMQP | `q.inventarioConfirmacion` | `ListenerConfInventario` |
| JDBC | `RepoDespacho` | `DB DESPACHO` |
| JDBC | `RepoOutboxDespacho` | `DB DESPACHO` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl msdespacho -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=msdespacho
```

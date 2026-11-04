# SINBackEnd

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Sistema de Inventarios. Registra la salida del bien de cada compra, una sola vez aunque el mensaje se repita, y la confirma por outbox.

## Despliegue

Artefacto `SINBackEnd.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| SIN SERVER :9002 | 9002 | MAQUINA 3 · 10.43.99.45 | Tomcat embebido |

Clases del diagrama: `ListenerInventario`, `RepoInventario`, `RepoOutboxSIN`, `SrvInventario`, `WorkerOutboxSIN`.

## Endpoints

No expone API de negocio: trabaja por colas. Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.inventarioSolicitud` | `ListenerInventario` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.inventarioConfirmacion` | `InventarioConfirmado` | `WorkerOutboxSIN` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-sin:5432/sin` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `sin` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxSIN` | `q.inventarioConfirmacion` |
| AMQP | `q.inventarioSolicitud` | `ListenerInventario` |
| JDBC | `RepoInventario` | `DB SIN` |
| JDBC | `RepoOutboxSIN` | `DB SIN` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl sinbackend -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=sinbackend
```

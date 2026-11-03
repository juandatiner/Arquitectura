# SENBackEnd

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Sistema de Envíos. Programa el envío de cada compra, una sola vez aunque el mensaje se repita, y confirma la fecha programada por outbox.

## Despliegue

Artefacto `SENBackEnd.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| SEN SERVER :9003 | 9003 | MAQUINA 3 · 10.43.99.45 | Tomcat embebido |

Clases del diagrama: `ListenerEnvio`, `RepoEnvio`, `RepoOutboxSEN`, `SrvEnvio`, `WorkerOutboxSEN`.

## Endpoints

No expone API de negocio: trabaja por colas. Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.envioSolicitud` | `ListenerEnvio` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.envioConfirmacion` | `EnvioConfirmado` | `WorkerOutboxSEN` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-sen:5432/sen` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `sen` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxSEN` | `q.envioConfirmacion` |
| AMQP | `q.envioSolicitud` | `ListenerEnvio` |
| JDBC | `RepoEnvio` | `DB SEN` |
| JDBC | `RepoOutboxSEN` | `DB SEN` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl senbackend -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=senbackend
```

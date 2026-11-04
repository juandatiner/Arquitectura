# MSSuscripcion

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Administra las suscripciones de los clientes (`NUEVAS_SUBASTAS`, `CAMBIOS_OFERTA`) y convierte los eventos de subasta en notificaciones para los suscriptores.

## Despliegue

Artefacto `MSSuscripcion.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSSuscripcion SERVER :8084 | 8084 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `ListenerEventoSubasta`, `ListenerLiberarBienSus`, `RepoOutboxSuscripcion`, `RepoSuscripcion`, `SrvSuscripcion`, `WSSuscripcion`, `WorkerOutboxSuscripcion`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/mssuscripcion.yaml`](/docs/contratos/openapi/mssuscripcion.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/suscripciones` | `suscribirse` | cuerpo `SolicitudSuscripcion` {criterio, cliente} | 201 `Suscripcion` | Pasos II.7b (SrvRuteo → WSSuscripcion suscribirse) e II.8b (→ SrvSuscripcion). |
| POST | `/suscripciones/cancelar` | `cancelarSuscripcion` | cuerpo `SolicitudCancelacion` {idSuscripcion, cliente} | 200 `Suscripcion` | Pasos II.7b (SrvRuteo → WSSuscripcion cancelarSuscripcion) e II.8b (→ SrvSuscripcion). |

Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.LiberarBienSubasta.suscripcion` | `ListenerLiberarBienSus` |
| `q.cambioOfertaMayor` | `ListenerEventoSubasta` |
| `q.nuevaSubasta` | `ListenerEventoSubasta` |
| `q.subastaIniciada` | `ListenerEventoSubasta` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.notificacion` | `NotificacionSuscripcion` | `WorkerOutboxSuscripcion` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-suscripcion:5432/suscripcion` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `suscripcion` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxSuscripcion` | `q.notificacion` |
| AMQP | `q.LiberarBienSubasta` | `ListenerLiberarBienSus` |
| AMQP | `q.cambioOfertaMayor` | `ListenerEventoSubasta` |
| AMQP | `q.nuevaSubasta` | `ListenerEventoSubasta` |
| AMQP | `q.subastaIniciada` | `ListenerEventoSubasta` |
| HTTP | `SrvRuteo` | `WSSuscripcion` |
| JDBC | `RepoOutboxSuscripcion` | `DB Suscripcion` |
| JDBC | `RepoSuscripcion` | `DB Suscripcion` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl mssuscripcion -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=mssuscripcion
```

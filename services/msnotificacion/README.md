# MSNotificacion

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Entrega las notificaciones de `q.notificacion` por correo (SMTP) y, si el mensaje lo pide, por SMS. Reintenta con espera creciente; al agotar los intentos el mensaje pasa a `q.notificacion.dlq`.

## Despliegue

Artefacto `MSNotificacion.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSNotificacion SERVER :8089 | 8089 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `IntegraMail`, `IntegraSMS`, `ListenerNotificar`, `RepoNotificar`, `SrvNotificar`.

## Endpoints

No expone API de negocio: trabaja por colas. Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.notificacion` | `ListenerNotificar` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-notificacion:5432/notificacion` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `notificacion` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |
| `MAIL_HOST` | Host SMTP | `spring.mail.host` | `mail` |
| `MAIL_REMITENTE` | Remitente de los correos | `sso.notificacion.remitente` | `${MAIL_REMITENTE}` |
| `RUTA_SMS` | URL de SMS SERVER | `sso.notificacion.url-sms` | `http://sms:9004` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `q.notificacion` | `ListenerNotificar` |
| HTTP(SMS) | `IntegraSMS` | `SMS GATEWAY` |
| JDBC | `RepoNotificar` | `DB NOTIFICACION` |
| SMTP | `IntegraMail` | `CORREO` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl msnotificacion -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=msnotificacion
```

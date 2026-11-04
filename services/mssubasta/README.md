# MSSubasta

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Registra ofertas con control optimista de versión, abre y cierra subastas con sus timers, y publica los cambios por outbox. Libera la subasta cuando una compra se compensa.

## Despliegue

Artefacto `MSSubasta.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSSubasta SERVER :8083 | 8083 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `ListenerLiberarBienSub`, `RepoOferta`, `RepoOutboxSubasta`, `RepoSubasta`, `SrvSubasta`, `TimerAperturaSubasta`, `TimerCierreSubasta`, `WSSubasta`, `WorkerOutboxSubasta`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/mssubasta.yaml`](/docs/contratos/openapi/mssubasta.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/subastas/ofertas` | `registrarOferta` | cuerpo `SolicitudOferta` {idSubasta, cliente, valor} | 202 `OfertaRegistrada` | Pasos IV.7 (SrvRuteo → WSSubasta) y IV.8 (WSSubasta → SrvSubasta). |

Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.LiberarBienSubasta.subasta` | `ListenerLiberarBienSub` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.cambioOfertaMayor` | `CambioOfertaMayor` | `WorkerOutboxSubasta` |
| `q.subastaCerrada` | `SubastaCerrada` | `WorkerOutboxSubasta` |
| `q.subastaIniciada` | `SubastaIniciada` | `WorkerOutboxSubasta` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-subasta:5432/subasta` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `subasta` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxSubasta` | `q.cambioOfertaMayor` |
| AMQP | `WorkerOutboxSubasta` | `q.subastaCerrada` |
| AMQP | `WorkerOutboxSubasta` | `q.subastaIniciada` |
| AMQP | `q.LiberarBienSubasta` | `ListenerLiberarBienSub` |
| HTTP | `SrvRuteo` | `WSSubasta` |
| JDBC | `RepoOferta` | `DB SUBASTA` |
| JDBC | `RepoOutboxSubasta` | `DB SUBASTA` |
| JDBC | `RepoSubasta` | `DB SUBASTA` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl mssubasta -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=mssubasta
```

# MSCatalogo

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Consulta paginada del catálogo y publicación de subastas (de PENDIENTE a PUBLICADA). Anuncia cada subasta publicada en `q.nuevaSubasta` por outbox y libera la subasta cuando una compra se compensa.

## Despliegue

Artefacto `MSCatalogo.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSCatalogo SERVER :8082 | 8082 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `ListenerLiberarBienCat`, `RepoCatalogo`, `RepoOutboxCatalogo`, `SrvCatalogo`, `WSCatalogo`, `WorkerOutboxCatalogo`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/mscatalogo.yaml`](/docs/contratos/openapi/mscatalogo.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| GET | `/catalogo` | `consultarCatalogo` | query `texto`, `categoria`, `pagina`, `tamano` | 200 `PaginaCatalogo` | Pasos II.7a (SrvRuteo → WSCatalogo) y II.8a (WSCatalogo → SrvCatalogo). |
| POST | `/catalogo/publicar` | `publicarNuevaSubasta` | cuerpo `SolicitudPublicacion` {idSubasta, admin} | 200 `SubastaPublicada` | Pasos III.7 (SrvRuteo → WSCatalogo) y III.8 (WSCatalogo → SrvCatalogo). |

Salud: `GET /actuator/health`.

## Eventos

Esquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml).

Consume:

| Cola | Clase |
|---|---|
| `q.LiberarBienSubasta.catalogo` | `ListenerLiberarBienCat` |

Publica (outbox):

| Exchange | Evento | Clase |
|---|---|---|
| `q.nuevaSubasta` | `NuevaSubasta` | `WorkerOutboxCatalogo` |

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-catalogo:5432/catalogo` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `catalogo` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `MOM_HOST` | Host de RabbitMQ | `spring.rabbitmq.host` | `mom` |
| `MOM_USUARIO` | Usuario de RabbitMQ | `spring.rabbitmq.username` | `${RABBITMQ_USUARIO}` |
| `MOM_CLAVE` | Clave de RabbitMQ | `spring.rabbitmq.password` | `${RABBITMQ_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| AMQP | `WorkerOutboxCatalogo` | `q.nuevaSubasta` |
| AMQP | `q.LiberarBienSubasta` | `ListenerLiberarBienCat` |
| HTTP | `SrvRuteo` | `WSCatalogo` |
| JDBC | `RepoCatalogo` | `DB CATALOGO` |
| JDBC | `RepoOutboxCatalogo` | `DB CATALOGO` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl mscatalogo -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=mscatalogo
```

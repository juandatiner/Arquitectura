# MSValidacion

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Consulta a la OFAC por la cédula del comprador y guarda el resultado. Un resultado definitivo (`APROBADO`, `RECHAZADO`) no se vuelve a consultar; `ENPROCESO` sí.

## Despliegue

Artefacto `MSValidacion.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSValidacion SERVER :8087 | 8087 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `IntegraOFAC`, `RepoValidar`, `SrvValidarOFAC`, `WSValidacion`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/msvalidacion.yaml`](/docs/contratos/openapi/msvalidacion.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/validaciones` | `validar` | cuerpo `SolicitudValidacion` {numeroCompra, cedula} | 200 `ResultadoValidacion` | Pasos VI.14 (IntegraValidacion → WSValidacion validar(numeroCompra, cedula)) y VI.15 (→ SrvValidarOFAC). |

Salud: `GET /actuator/health`.

## Eventos

No publica ni consume colas.

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-validar:5432/validar` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `validar` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `RUTA_OFAC` | URL de WSOfac | `sso.validacion.url-ofac` | `http://ofacbackend:9001` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP/REST | `IntegraOFAC` | `WSOfac` |
| HTTP/REST | `IntegraValidacion` | `WSValidacion` |
| JDBC | `RepoValidar` | `DB VALIDAR` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl msvalidacion -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=msvalidacion
```

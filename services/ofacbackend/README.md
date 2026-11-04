# OFACBackEnd

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Sistema externo OFAC. Responde el estado de una cédula según DB OFAC: sin reporte `APROBADO`, `REPORTADO` da `RECHAZADO` y `EN_ESTUDIO` da `ENPROCESO`.

## Despliegue

Artefacto `OFACBackEnd.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| OFAC SERVER :9001 | 9001 | MAQUINA 3 · 10.43.99.45 | Tomcat embebido |

Clases del diagrama: `RepoOFAC`, `SrvValidacionOFAC`, `WSOfac`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/ofacbackend.yaml`](/docs/contratos/openapi/ofacbackend.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| GET | `/ofac/{cedula}` | `validarCedula` | — | 200 `RespuestaOFAC` | Pasos VI.18 (IntegraOFAC → WSOfac validarCedula(cedula)) y VI.19 (→ SrvValidacionOFAC). |

Salud: `GET /actuator/health`.

## Eventos

No publica ni consume colas.

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-ofac:5432/ofac` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `ofac` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP/REST | `IntegraOFAC` | `WSOfac` |
| JDBC | `RepoOFAC` | `DB OFAC` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl ofacbackend -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=ofacbackend
```

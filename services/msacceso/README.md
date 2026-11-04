# MSAcceso

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Autentica al cliente: valida usuario y clave contra el AD por LDAP, lee sus datos en DB ACCESO (que no guarda claves) y emite el token RS256.

## Despliegue

Artefacto `MSAcceso.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| MSAcceso SERVER :8081 | 8081 | MAQUINA 2 · 10.43.100.238 | Tomcat embebido |

Clases del diagrama: `IntegraLoginAD`, `RepoLogin`, `SrvLogin`, `WSAcceso`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/msacceso.yaml`](/docs/contratos/openapi/msacceso.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/acceso/login` | `autenticar` | cuerpo `Credenciales` {usuario, clave} | 200 `RespuestaAcceso` | Pasos I.7 (SrvRuteo → WSAcceso) y I.8 (WSAcceso → SrvLogin). |

Salud: `GET /actuator/health`.

## Eventos

No publica ni consume colas.

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_URL` | URL JDBC de su base de datos | `spring.datasource.url` | `jdbc:postgresql://db-acceso:5432/acceso` |
| `DB_USUARIO` | Usuario de su base de datos | `spring.datasource.username` | `acceso` |
| `DB_CLAVE` | Clave de su base de datos | `spring.datasource.password` | `${DB_CLAVE}` |
| `AD_URL` | URL LDAP del AD | `sso.ad.url` | `ldap://ad:389` |
| `TOKEN_CLAVE_PRIVADA` | Ruta del PEM con la clave privada del token | `sso.token.archivo-clave-privada` | `/run/secrets/token_privada` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP | `SrvRuteo` | `WSAcceso` |
| JDBC | `RepoLogin` | `DB ACCESO` |
| LDAP | `IntegraLoginAD` | `AD` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl msacceso -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=msacceso
```

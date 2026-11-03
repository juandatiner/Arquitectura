# YoPagoBackEnd

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Sistema externo YoPago. Guarda las compras pendientes, valida las credenciales del cliente en YoPago, registra el pago y lo reporta a SSO por HTTPS, reintentando en segundo plano si SSO no responde.

## Despliegue

Artefacto `YoPagoBackEnd.dll`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| YoPago SERVER :5001 | 5001 | MAQUINA 3 · 10.43.99.45 | Kestrel .NET 8 |

Clases del diagrama: `IntegraSSO`, `RepoYoPago`, `SrvYoPago`, `WSCompraPendiente`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/yopagobackend.yaml`](/docs/contratos/openapi/yopagobackend.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/compras-pendientes` | `registrarCompraPendiente` | cuerpo `SolicitudPendiente` {cedula, numeroCompra, valorTotal} | 200 `CompraPendiente` | Pasos VII.7 (IntegraYoPago → WSCompraPendiente registrarCompraPendiente) y VII.8 (→ SrvYoPago). |
| POST | `/compras-pendientes/consulta` | `consultarCompraPendiente` | cuerpo `Consulta` {cedula, clave, numeroCompra} | 200 `IReadOnlyList<CompraPendiente>` | Paso VII.16 (ProxyWSYoPagoWeb → WSCompraPendiente consultarCompraPendiente): las compras SSO pendientes del cliente. |
| POST | `/compras-pendientes/{numeroCompra}/pagar` | `pagar` | cuerpo `DatosPago` {cedula, clave, medio, titular} | 200 `ResultadoPago` | Pasos VII.16 (ProxyWSYoPagoWeb → WSCompraPendiente pagar) y VII.17 (→ SrvYoPago pagar(numeroCompra, datosPago)). |

Salud: `GET /health`.

## Eventos

No publica ni consume colas.

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `DB_HOST` | Host de su base de datos | `Host` | `db-yopago` |
| `DB_NOMBRE` | Nombre de su base de datos | `Database` | `yopago` |
| `DB_USUARIO` | Usuario de su base de datos | `Username` | `yopago` |
| `DB_CLAVE` | Clave de su base de datos | `Password` | `${DB_CLAVE}` |
| `CERTIFICADO_SSO` | Ruta del certificado TLS del balanceador | `Program.cs` | `/run/secrets/certificado_sso` |
| `CREDENCIAL_YOPAGO` | Credencial compartida del reporte de pago | `credencial` | `${CREDENCIAL_YOPAGO}` |
| `RUTA_SSO` | URL HTTPS de Nginx hacia SSO | `Program.cs` | `https://balanceador:443` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP | `ProxyWSYoPagoWeb` | `WSCompraPendiente` |
| HTTP/REST | `IntegraYoPago` | `WSCompraPendiente` |
| HTTPS | `IntegraSSO` | `Nginx` |
| JDBC | `RepoYoPago` | `DB YOPAGO` |

## Construir y probar

```bash
make test-dotnet
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=yopagobackend
```

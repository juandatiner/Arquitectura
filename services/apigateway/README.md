# APIGateway

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Único punto de entrada de la DMZ. `SrvSeguridadToken` valida el token RS256 emitido por MSAcceso (y la credencial de YoPago en el reporte de pago) y `SrvRuteo` envía la petición al componente interno. Sin lógica de negocio: devuelve la respuesta del componente sin modificarla.

## Despliegue

Artefacto `APIGateway.jar`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| APIGATEWAY SERVER :8080 | 8080 | MAQUINA 1 · 10.43.99.2 | Tomcat embebido |

Clases del diagrama: `SrvRuteo`, `SrvSeguridadToken`, `WSGateway`.

## Endpoints

Contrato completo, con errores: [`docs/contratos/openapi/apigateway.yaml`](/docs/contratos/openapi/apigateway.yaml).

| Método | Ruta | Operación | Entrada | Éxito | Pasos |
|---|---|---|---|---|---|
| POST | `/api/acceso/login` | `autenticar` | cuerpo `Credenciales` {usuario, clave} | 200 `RespuestaAcceso` de MSAcceso.jar | Pasos I.4 (Nginx → WSGateway) e I.5 (→ SrvSeguridadToken evaluarAcceso). |
| GET | `/api/catalogo` | `consultarCatalogo` | query `texto`, `categoria`, `pagina`, `tamano` | 200 `PaginaCatalogo` de MSCatalogo.jar | Pasos II.4 (Nginx → WSGateway, GET /api/catalogo) e II.5 (→ validarYAutorizar). |
| POST | `/api/suscripciones` | `suscribirse` | cuerpo `CriterioSuscripcion` {tipo, valor} | 201 `Suscripcion` de MSSuscripcion.jar | Pasos II.4 (Nginx → WSGateway, POST /api/suscripciones) e II.5. |
| DELETE | `/api/suscripciones/{idSuscripcion}` | `cancelarSuscripcion` | — | 200 `Suscripcion` de MSSuscripcion.jar | Pasos II.4 e II.5, camino b de II.7b (cancelarSuscripcion). |
| POST | `/api/catalogo/publicar` | `publicarNuevaSubasta` | cuerpo `Publicacion` {idSubasta} | 200 `SubastaPublicada` de MSCatalogo.jar | Pasos III.4 (Nginx → WSGateway, POST /api/catalogo/publicar) e III.5. |
| POST | `/api/subastas/{idSubasta}/ofertas` | `registrarOferta` | cuerpo `Oferta` {valor} | 202 `OfertaRegistrada` de MSSubasta.jar | Pasos IV.4 (Nginx → WSGateway, POST /api/subastas/{id}/ofertas) e IV.5. |
| GET | `/api/compras` | `consultarMisCompras` | — | 200 `List<Compra>` de MSCompra.jar | VI.1 consultarMisCompras sobre las flechas de VI.4 – VI.8 (observación 6). |
| POST | `/api/compras/{numeroCompra}/iniciar` | `iniciarCompra` | — | 202 `CompraIniciada` de MSCompra.jar | Pasos VI.4 (Nginx → WSGateway, POST /api/compras/{numero}/iniciar) y VI.5. |
| POST | `/api/pagos/reporte` | `reportarPago` | cuerpo `ReportePago` {cedula, numeroCompra, valorPagado, credencial} | 200 `ResultadoConciliacion` de MSPago.dll | Pasos VII.21 (Nginx → WSGateway, POST /api/pagos/reporte desde YoPago) y VII.22. |

Salud: `GET /actuator/health`.

## Eventos

No publica ni consume colas.

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `RUTA_MSACCESO` | URL de WSAcceso | `sso.rutas.ms-acceso` | `http://msacceso:8081` |
| `RUTA_MSCATALOGO` | URL de WSCatalogo | `sso.rutas.ms-catalogo` | `http://mscatalogo:8082` |
| `RUTA_MSSUBASTA` | URL de WSSubasta | `sso.rutas.ms-subasta` | `http://mssubasta:8083` |
| `RUTA_MSSUSCRIPCION` | URL de WSSuscripcion | `sso.rutas.ms-suscripcion` | `http://mssuscripcion:8084` |
| `RUTA_BALANCEADOR_COMPRA` | URL de Nginx hacia el upstream msCompra | `sso.rutas.balanceador-compra` | `http://balanceador:8085` |
| `RUTA_MSPAGO` | URL de WSPagoYoPago | `sso.rutas.ms-pago` | `http://mspago:5086` |
| `TOKEN_CLAVE_PUBLICA` | Ruta del PEM con la clave pública del token | `sso.token.archivo-clave-publica` | `/run/secrets/token_publica` |
| `CREDENCIAL_YOPAGO` | Credencial compartida del reporte de pago | `sso.credencial-yo-pago` | `${CREDENCIAL_YOPAGO}` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP | `Nginx` | `WSGateway` |
| HTTP | `SrvRuteo` | `Nginx` |
| HTTP | `SrvRuteo` | `WSAcceso` |
| HTTP | `SrvRuteo` | `WSCatalogo` |
| HTTP | `SrvRuteo` | `WSPagoYoPago` |
| HTTP | `SrvRuteo` | `WSSubasta` |
| HTTP | `SrvRuteo` | `WSSuscripcion` |

## Construir y probar

```bash
cd services && ./mvnw -B -pl apigateway -am verify
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=apigateway
```

# YoPagoFrontEnd

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Sitio de YoPago (ASP.NET Core MVC con Razor): ingreso, compras pendientes y pago. Solo presentación: todo lo resuelve `WSCompraPendiente` a través de `ProxyWSYoPagoWeb`.

## Despliegue

Artefacto `YoPagoFrontEnd.dll`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| YoPagoWeb SERVER :5000 | 5000 | MAQUINA 1 · 10.43.99.2 | Kestrel .NET 8 |

Clases del diagrama: `PantallaWebYoPago`, `ProxyWSYoPagoWeb`.

## Endpoints

Devuelven vistas Razor (`Views/PantallaWebYoPago/`), no JSON, y los POST exigen el token antiforgery del formulario.

| Método | Ruta | Operación | Entrada | Pasos |
|---|---|---|---|---|
| GET | `/` | `Ingresar` | `compra` | Formulario de ingreso (cédula y contraseña). `compra` llega en la URL de pago del SMS. |
| POST | `/consultar` | `consultarCompraPendiente` | `numeroCompra` | — |
| POST | `/pagar` | `confirmarPago` | `numeroCompra`, `medio`, `titular` | Paso VII.14 (Browser → PantallaWebYoPago confirmarPago(...)) y VII.15 (→ ProxyWSYoPagoWeb pagar(numeroCompra, datosPago)). |
| POST | `/salir` | `Salir` | — | — |

Salud: `GET /health`.

## Eventos

No publica ni consume colas.

## Variables de entorno

Todas son obligatorias: sin valor por defecto, el servicio no arranca. Llegan desde `deploy/maquina-N/.env`.

| Variable | Qué es | Dónde se usa | En `deploy/dev` |
|---|---|---|---|
| `RUTA_YOPAGO` | URL de WSCompraPendiente | `rutaYoPago` | `http://yopagobackend:5001` |

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP | `Browser` | `PantallaWebYoPago` |
| HTTP | `ProxyWSYoPagoWeb` | `WSCompraPendiente` |

## Construir y probar

```bash
make test-dotnet
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=yopagofrontend
```

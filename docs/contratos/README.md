# Contratos de API y de eventos

Se generan desde el código, no se editan a mano:

```bash
make contratos            # regenera esta carpeta
make contratos-comprobar  # falla si algo quedó desactualizado
```

| Archivo | Contenido |
|---|---|
| `openapi/<componente>.yaml` | OpenAPI 3.1 de cada componente con API HTTP: rutas, cuerpos, respuestas y errores |
| `asyncapi.yaml` | AsyncAPI 3.0 de cada cola `q.*`: exchange, productores, consumidores y esquema del mensaje |
| `ejemplos/*.json` | Forma de cada evento y del reporte de pago Java → .NET (VII.24). Los valores son marcadores (`<cedula>`), no datos |

El significado de cada evento y sus pasos está en [`docs/modelo/eventos.md`](../modelo/eventos.md).

## Cómo se obtienen

`tools/docs/fuentes.py` lee las anotaciones y los `record` de Java y C#, `@RabbitListener`, `cola()` y `deploy/mom/definiciones/colas.json`.
La generación se detiene si las colas, productores y consumidores del código no coinciden con las flechas AMQP de `docs/modelo/modelo-canonico.json` o con `colas.json`.

Son manuales, en tablas de `tools/docs/generar_contratos.py`, tres datos que el código no expresa en un solo lugar: qué excepciones lanza cada operación (el código HTTP sí se lee de su manejador), a qué destino enruta `SrvRuteo` cada operación de `WSGateway` y los campos que el contrato declara nulos.

## Errores

- `WSGateway` añade 401, 403 y 503 propios y devuelve sin cambios los errores del componente interno.
- Los errores manejados usan Problem Details (RFC 9457), `application/problem+json`.
- Una validación fallida en Java responde 400 con el cuerpo por defecto de Spring Boot (`timestamp`, `status`, `error`, `path`), no con Problem Details.

## Fuera de estos contratos

- `YoPagoFrontEnd` devuelve vistas Razor: sus rutas están en su README.
- `SSOWeb` no expone API: sus llamadas están en su README.
- El nodo `SMS SERVER :9004` está descrito en `deploy/sms/servidor.py`.
- springdoc y Swagger UI no están activados en los servicios Java: estos archivos son la referencia.

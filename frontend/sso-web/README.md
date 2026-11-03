# SSOWeb

> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`.

## Responsabilidad

Pantallas de SSO (Angular): login, catálogo, suscripciones, ofertas, mis compras y publicación. Solo presentación: toda decisión la toma el backend a través de `ProxyWSSubastaWeb`.

## Despliegue

Artefacto `SSOWeb.zip`.

| Nodo | Puerto | Máquina | Tecnología |
|---|---|---|---|
| SSOWeb SERVER :80 | 80 | MAQUINA 1 · 10.43.99.2 | Nginx |

Clases del diagrama: `PantallaWebSubasta`, `ProxyWSSubastaWeb`.

## Endpoints

No expone API: Nginx sirve archivos estáticos (`nginx.conf`). `ProxyWSSubastaWeb` llama al mismo Nginx con rutas relativas; contrato en [`docs/contratos/openapi/apigateway.yaml`](/docs/contratos/openapi/apigateway.yaml).

| Operación | Método | Ruta |
|---|---|---|
| `autenticar` | POST | `/api/acceso/login` |
| `consultarCatalogo` | GET | `/api/catalogo` |
| `suscribirse` | POST | `/api/suscripciones` |
| `cancelarSuscripcion` | DELETE | `/api/suscripciones/{idSuscripcion}` |
| `publicarNuevaSubasta` | POST | `/api/catalogo/publicar` |
| `registrarOferta` | POST | `/api/subastas/{idSubasta}/ofertas` |
| `consultarMisCompras` | GET | `/api/compras` |
| `iniciarCompra` | POST | `/api/compras/{numeroCompra}/iniciar` |

## Eventos

No publica ni consume colas.

## Variables de entorno

Ninguna: las rutas son relativas al Nginx que sirve la página.

## Conexiones del diagrama

| Protocolo | De | A |
|---|---|---|
| HTTP | `Browser` | `PantallaWebSubasta` |
| HTTP | `ProxyWSSubastaWeb` | `Nginx` |

## Construir y probar

```bash
npm ci
npm run lint
npm test -- --watch=false
npm run build
make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=ssoweb   # desde la raíz; empaqueta SSOWeb.zip
```

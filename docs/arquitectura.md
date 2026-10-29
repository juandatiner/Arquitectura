# Arquitectura SSO — vista de despliegue y componentes

Fuente: diagrama *Arquitectura Integrada JavaEE-.NET* del modelo VPP.

## Máquinas

| Máquina | IP | Perímetro | Rol |
|---------|----|-----------|-----|
| Máquina 1 | 10.43.99.2 | DMZ (web) | Entrada pública, presentación |
| Máquina 2 | 10.43.100.238 | Red interna | Núcleo de negocio SSO y MOM |
| Máquina 3 | 10.43.99.45 | Red interna | Réplica de compra, .NET, sistemas externos e internos |

La lógica de presentación (Máquina 1) y la de negocio (Máquinas 2 y 3) quedan en máquinas separadas, como pide el enunciado.

## Componentes por máquina

### Máquina 1 · 10.43.99.2

| Nodo | Puerto | Artefacto | Clases |
|------|--------|-----------|--------|
| BALANCEADOR DE CARGAS | 80 | Nginx | `Nginx` (upstream `msCompra` con `least_conn`) |
| SSOWeb SERVER | 80 (interno) | `SSOWeb.zip` (Angular) | `PantallaWebSubasta`, `ProxyWSSubastaWeb` |
| APIGATEWAY SERVER | 8080 | `APIGateway.jar` | `WSGateway`, `SrvSeguridadToken`, `SrvRuteo` |
| YoPagoWeb SERVER | 5000 | `YoPagoFrontEnd.dll` | `PantallaWebYoPago`, `ProxyWSYoPagoWeb` |

### Máquina 2 · 10.43.100.238

| Nodo | Puerto | Artefacto | Clases | BD |
|------|--------|-----------|--------|----|
| MSAcceso | 8081 | `MSAcceso.jar` | `WSAcceso`, `SrvLogin`, `RepoLogin`, `IntegraLoginAD` | DB ACCESO |
| MSCatalogo | 8082 | `MSCatalogo.jar` | `WSCatalogo`, `SrvCatalogo`, `RepoCatalogo`, `RepoOutboxCatalogo`, `WorkerOutboxCatalogo`, `ListenerLiberarBienCat` | DB CATALOGO |
| MSSubasta | 8083 | `MSSubasta.jar` | `WSSubasta`, `SrvSubasta`, `RepoSubasta`, `RepoOferta`, `RepoOutboxSubasta`, `WorkerOutboxSubasta`, `TimerAperturaSubasta`, `TimerCierreSubasta`, `ListenerLiberarBienSub` | DB SUBASTA |
| MSSuscripcion | 8084 | `MSSuscripcion.jar` | `WSSuscripcion`, `SrvSuscripcion`, `RepoSuscripcion`, `RepoOutboxSuscripcion`, `WorkerOutboxSuscripcion`, `ListenerEventoSubasta`, `ListenerLiberarBienSus` | DB Suscripcion |
| MSCompra réplica 1 | 8085 | `MSCompra.jar` | `WSCompra`, `SrvCompraSaga`, `RepoCompra`, `RepoOutboxCompra`, `WorkerOutboxCompra`, `TimerValidacionOFAC`, `IntegraValidacion`, `ListenerSubastaCerrada`, `ListenerPago`, `ListenerConfDespacho` | DB COMPRA (compartida por réplicas) |
| MSValidacion | 8087 | `MSValidacion.jar` | `WSValidacion`, `SrvValidarOFAC`, `RepoValidar`, `IntegraOFAC` | DB VALIDAR |
| MSDespacho | 8088 | `MSDespacho.jar` | `SrvDespacho`, `RepoDespacho`, `RepoOutboxDespacho`, `WorkerOutboxDespacho`, `ListenerDespachoSolicitud`, `ListenerConfInventario`, `ListenerConfEnvio` | DB DESPACHO |
| MSNotificacion | 8089 | `MSNotificacion.jar` | `ListenerNotificar`, `SrvNotificar`, `RepoNotificar`, `IntegraMail`, `IntegraSMS` | DB NOTIFICACION |
| MOM SERVER | 5672 | RabbitMQ | 14 colas (ver abajo) | — |

### Máquina 3 · 10.43.99.45

| Nodo | Puerto | Artefacto | Clases | BD |
|------|--------|-----------|--------|----|
| MSCompra réplica 2 | 8085 | `MSCompra.jar` | igual que réplica 1 | DB COMPRA (en Máquina 2) |
| MSPago (.NET) | 5086 | `MSPago.dll` | `WSPagoYoPago`, `SrvPago`, `RepoPago`, `RepoOutboxPago`, `WorkerOutboxPago`, `ListenerPagoSolicitud`, `IntegraYoPago` | DB PAGO |
| YoPago (.NET, externo) | 5001 | `YoPagoBackEnd.dll` | `WSCompraPendiente`, `SrvYoPago`, `RepoYoPago`, `IntegraSSO` | DB YOPAGO |
| OFAC (externo) | 9001 | `OFACBackEnd.jar` | `WSOfac`, `SrvValidacionOFAC`, `RepoOFAC` | DB OFAC |
| SIN (interno) | 9002 | `SINBackEnd.jar` | `ListenerInventario`, `SrvInventario`, `RepoInventario`, `RepoOutboxSIN`, `WorkerOutboxSIN` | DB SIN |
| SEN (interno) | 9003 | `SENBackEnd.jar` | `ListenerEnvio`, `SrvEnvio`, `RepoEnvio`, `RepoOutboxSEN`, `WorkerOutboxSEN` | DB SEN |
| AD SERVER | 389 | OpenLDAP | `AD` | — |
| MAIL SERVER | 1025 | Mailpit (SMTP) | `CORREO` | — |
| SMS SERVER | 9004 | Simulador HTTP | `SMS GATEWAY` | — |

## Colas RabbitMQ

| Cola | Productor | Consumidor(es) | CU |
|------|-----------|----------------|----|
| `q.nuevaSubasta` | WorkerOutboxCatalogo | ListenerEventoSubasta | III |
| `q.subastaIniciada` | WorkerOutboxSubasta | ListenerEventoSubasta | III |
| `q.cambioOfertaMayor` | WorkerOutboxSubasta | ListenerEventoSubasta | IV |
| `q.subastaCerrada` | WorkerOutboxSubasta | ListenerSubastaCerrada (r1 **o** r2) | V |
| `q.notificacion` | Outbox de Suscripcion, Compra, Pago | ListenerNotificar | IX |
| `q.pagoSolicitud` | WorkerOutboxCompra (Java) | ListenerPagoSolicitud (.NET) | VII |
| `q.pagoConfirmado` | WorkerOutboxPago (.NET) | ListenerPago (r1 **o** r2) | VII |
| `q.despachoSolicitud` | WorkerOutboxCompra | ListenerDespachoSolicitud | VIII |
| `q.inventarioSolicitud` | WorkerOutboxDespacho | ListenerInventario (SIN) | VIII |
| `q.inventarioConfirmacion` | WorkerOutboxSIN | ListenerConfInventario | VIII |
| `q.envioSolicitud` | WorkerOutboxDespacho | ListenerEnvio (SEN) | VIII |
| `q.envioConfirmacion` | WorkerOutboxSEN | ListenerConfEnvio | VIII |
| `q.despachoConfirmado` | WorkerOutboxDespacho | ListenerConfDespacho (r1 **o** r2) | VIII |
| `q.LiberarBienSubasta` | WorkerOutboxCompra | ListenerLiberarBienSub, ListenerLiberarBienCat, ListenerLiberarBienSus (fan-out) | X |

`q.LiberarBienSubasta` lo consumen tres servicios, por lo que se implementa como *exchange fanout* con una cola por consumidor.
Los mensajes entre Java y .NET usan JSON con nombres de campo en camelCase.

## Estados de la compra (saga en MSCompra)

```
CREADA ─iniciarCompra─▶ EN_VALIDACION ─OFAC APROBADO─▶ ESPERANDO_PAGO ─pago─▶ PAGO_CONFIRMADO ─SIN+SEN─▶ TERMINADA
                              │                              │                        │
                              ├─OFAC RECHAZADO─▶ CANCELADA    └─pago rechazado─▶ CANCELADA (compensa: libera bien)
                              └─sin respuesta (reintentos agotados)─▶ EN_REVISION      └─despacho agotado─▶ EN_REVISION
```

## Convención de colores por caso de uso

| CU | Nombre | Color | RGB |
|----|--------|-------|-----|
| I | Autenticar cliente | Verde | 0, 153, 0 |
| II | Consultar catálogo y suscribirse | Naranja | 255, 128, 0 |
| III | Publicar y abrir subasta | Morado | 128, 0, 255 |
| IV | Registrar oferta y avisar | Fucsia | 230, 0, 126 |
| V | Cerrar subasta y crear compra | Verde azulado | 0, 128, 128 |
| VI | Iniciar compra y validar OFAC | Mostaza | 204, 153, 0 |
| VII | Pagar en YoPago (.NET) | Azul oscuro | 0, 0, 160 |
| VIII | Despachar con SIN y SEN | Oliva | 128, 128, 0 |
| IX | Notificar al cliente | Azul | 0, 112, 192 |
| X | Compensar la compra | Café | 128, 64, 0 |

Los labels `cu:*` de GitHub usan los mismos colores.

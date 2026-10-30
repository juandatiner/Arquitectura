# Contratos de eventos (colas `q.*`)

Cada evento viaja como JSON UTF-8 en el exchange fanout con el nombre exacto de la cola del diagrama.
Propiedades AMQP: `message_id` = `idEvento` (para idempotencia), `type` = nombre del evento, `content_type` = `application/json`, persistente.
Los campos van en el orden en que se declaran aquí. Fechas en ISO-8601 UTC. Montos con punto decimal.

| Cola | Evento | Productor (paso) | Consumidor (paso) | Campos |
|------|--------|------------------|-------------------|--------|
| `q.nuevaSubasta` | `NuevaSubasta` | WorkerOutboxCatalogo (III.12) | ListenerEventoSubasta (III.18a) | `idEvento`, `idSubasta`, `idBien`, `descripcion`, `categoria`, `valorMinimo`, `fechaInicio`, `fechaCierre`, `publicadaPor` |
| `q.subastaIniciada` | `SubastaIniciada` | WorkerOutboxSubasta (III.17) | ListenerEventoSubasta (III.18b) | `idEvento`, `idSubasta`, `idBien`, `descripcion`, `valorMinimo`, `fechaCierre` |
| `q.cambioOfertaMayor` | `CambioOfertaMayor` | WorkerOutboxSubasta (IV.15) | ListenerEventoSubasta (IV.16) | `idEvento`, `idSubasta`, `descripcion`, `valor`, `cliente`, `anterior` (null si es la primera oferta) |
| `q.subastaCerrada` | `SubastaCerrada` | WorkerOutboxSubasta (V.7) | ListenerSubastaCerrada en **una** réplica de MSCompra (V.8a/b) | `idEvento`, `idSubasta`, `idBien`, `descripcion`, `resultado` (`ADJUDICADA`/`DESIERTA`), `ganador` (null si desierta), `valor` |
| `q.notificacion` | `Notificacion` (propiedad AMQP `type`: `Notificacion`, `NotificacionGanador`, `NotificacionCancelacion`, `NotificacionDemora`, `NotificacionCompraFinalizada`, `NotificacionRevisionManual`, `NotificacionPago`) | WorkerOutboxSuscripcion (III.23, IV.21), WorkerOutboxCompra (V.13, X.11), WorkerOutboxPago (VII.13) | ListenerNotificar (IX.1) | `idEvento`, `origen`, `idSubasta` o `numeroCompra`, `destinatario` `{nombre, correo, celular}`, `asunto`, `cuerpo`, `sms` (true = además del correo, SMS al celular: IX.6) |
| `q.pagoSolicitud` | `PagoSolicitud` | WorkerOutboxCompra (VII.2, Java → .NET) | ListenerPagoSolicitud de MSPago (VII.3) | `idEvento`, `numeroCompra`, `cedula`, `valorOfertado`, `descripcion`, `destinatario` |
| `q.pagoConfirmado` | `PagoConfirmado` | WorkerOutboxPago (VII.29, .NET → Java) | ListenerPago en **una** réplica de MSCompra (VII.30) | `idEvento`, `numeroCompra`, `cedula`, `valorPagado`, `referenciaPago`, `resultado` (`APROBADO`/`RECHAZADO`) |
| `q.despachoSolicitud` | `DespachoSolicitud` | WorkerOutboxCompra (VIII.2) | ListenerDespachoSolicitud (VIII.3) | `idEvento`, `numeroCompra`, `idBien`, `descripcion`, `direccion`, `destinatario` |
| `q.despachoConfirmado` | `DespachoConfirmado` | WorkerOutboxDespacho (VIII.20) | ListenerConfDespacho en **una** réplica de MSCompra (VIII.21) | `idEvento`, `numeroCompra` |
| `q.inventarioSolicitud` | `SolicitudInventario` | WorkerOutboxDespacho (VIII.8a) | ListenerInventario de SIN (VIII.9a) | `idEvento`, `numeroCompra`, `idBien` |
| `q.envioSolicitud` | `SolicitudEnvio` | WorkerOutboxDespacho (VIII.8b) | ListenerEnvio de SEN (VIII.9b) | `idEvento`, `numeroCompra`, `idBien`, `direccion` |
| `q.inventarioConfirmacion` | `InventarioConfirmado` | WorkerOutboxSIN (VIII.14a) | ListenerConfInventario (VIII.15a) | `idEvento`, `numeroCompra`, `idBien` |
| `q.envioConfirmacion` | `EnvioConfirmado` | WorkerOutboxSEN (VIII.14b) | ListenerConfEnvio (VIII.15b) | `idEvento`, `numeroCompra`, `fechaProgramada` |
| `q.LiberarBienSubasta` | `BienLiberado` | WorkerOutboxCompra (X.4) | ListenerLiberarBienSub (X.5a), ListenerLiberarBienCat (X.5b), ListenerLiberarBienSus (X.5c) | `idEvento`, `numeroCompra`, `idSubasta`, `motivo` |

Eventos sin cola (se guardan en el outbox con estado `REGISTRADO`, observación 8): `BienLiberadoConfirmado` (`idEvento`, `idSubasta`, `motivo`).

`cliente`, `anterior` y `ganador` = `{usuario, cedula, nombre, celular, correo, rol}` (identidad tomada del token).

Este documento crece con cada componente.

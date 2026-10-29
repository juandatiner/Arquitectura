# Contratos de eventos (colas `q.*`)

Cada evento viaja como JSON UTF-8 en el exchange fanout con el nombre exacto de la cola del diagrama.
Propiedades AMQP: `message_id` = `idEvento` (para idempotencia), `type` = nombre del evento, `content_type` = `application/json`, persistente.
Los campos van en el orden en que se declaran aquí. Fechas en ISO-8601 UTC. Montos con punto decimal.

| Cola | Evento | Productor (paso) | Consumidor (paso) | Campos |
|------|--------|------------------|-------------------|--------|
| `q.nuevaSubasta` | `NuevaSubasta` | WorkerOutboxCatalogo (III.12) | ListenerEventoSubasta (III.18a) | `idEvento`, `idSubasta`, `idBien`, `descripcion`, `categoria`, `valorMinimo`, `fechaInicio`, `fechaCierre`, `publicadaPor` |
| `q.LiberarBienSubasta` | `BienLiberado` | WorkerOutboxCompra (X.4) | ListenerLiberarBienSub (X.5a), ListenerLiberarBienCat (X.5b), ListenerLiberarBienSus (X.5c) | `idEvento`, `numeroCompra`, `idSubasta`, `motivo` |

Eventos sin cola (se guardan en el outbox con estado `REGISTRADO`, observación 8): `BienLiberadoConfirmado` (`idEvento`, `idSubasta`, `motivo`).

Este documento crece con cada componente.

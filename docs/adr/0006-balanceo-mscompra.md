# 0006 · Balanceo de MSCompra con Nginx `least_conn` y competencia en colas

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
El enunciado pide escalabilidad y balanceo del servicio de compra. El diseño tiene dos réplicas de MSCompra (Máquina 2 y Máquina 3).

## Decisión
- Tráfico HTTP: `SrvRuteo` envía las peticiones de compra al Nginx de Máquina 1, que balancea con `upstream msCompra { least_conn; }` entre ambas réplicas.
- Tráfico asíncrono: ambas réplicas consumen las mismas colas (`q.subastaCerrada`, `q.pagoConfirmado`, `q.despachoConfirmado`); RabbitMQ entrega cada mensaje a una sola réplica (*competing consumers*).
- Las réplicas no guardan estado en memoria: todo el estado vive en DB COMPRA. Las transiciones usan `cambiarEstadoSiActual` (UPDATE condicional) para evitar carreras.
- Los timers (`TimerValidacionOFAC`) toman lotes con `SKIP LOCKED` para que dos réplicas no procesen la misma compra.

## Consecuencias
- Se puede apagar una réplica y el sistema sigue funcionando (demostrable en la sustentación).

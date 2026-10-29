# 0002 · Outbox transaccional + consumidores idempotentes

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
El enunciado exige tolerar caídas de red e indisponibilidad de OFAC. El diseño publica eventos en RabbitMQ después de cambiar estado en la base de datos; publicar directamente perdería eventos si el broker cae entre el `commit` y el `publish`.

## Decisión
Cada servicio que emite eventos tiene `RepoOutbox<X>` (tabla `outbox`) y `WorkerOutbox<X>` (scheduler). El servicio guarda el cambio de negocio y el evento en la misma transacción. El worker toma lotes (`obtenerPendientes(tamañoLote)` con `FOR UPDATE SKIP LOCKED`), publica con *publisher confirms* y marca como enviado.
Los consumidores registran `idEvento` procesado (`yaProcesado`) para ignorar duplicados (entrega *at-least-once*).

## Consecuencias
- Entrega garantizada al menos una vez; duplicados controlados.
- Latencia extra igual al intervalo del worker (configurable, por defecto 1 s).
- `FOR UPDATE SKIP LOCKED` permite que las dos réplicas de MSCompra compartan la misma tabla outbox sin duplicar envíos.

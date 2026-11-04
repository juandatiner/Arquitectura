-- correlationId del evento: el worker lo publica en la cabecera AMQP. Las filas anteriores reciben uno propio.
ALTER TABLE outbox ADD COLUMN correlation_id VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text;

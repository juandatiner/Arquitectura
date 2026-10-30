-- DB SIN (DB SERVER :5432 · Máquina 3): salidas de inventario de los bienes vendidos (VIII.11a)
CREATE TABLE salida_inventario (
    numero_compra VARCHAR(20) PRIMARY KEY,
    id_bien       VARCHAR(20) NOT NULL UNIQUE,
    registrada_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Outbox transaccional (ADR 0002). JSON (no JSONB) para conservar el orden de los campos.
CREATE TABLE outbox (
    id_evento  UUID        PRIMARY KEY,
    tipo       VARCHAR(60) NOT NULL,
    cola       VARCHAR(60),
    payload    JSON        NOT NULL,
    estado     VARCHAR(12) NOT NULL CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'REGISTRADO')),
    intentos   INT         NOT NULL DEFAULT 0,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en TIMESTAMPTZ
);
CREATE INDEX outbox_pendientes ON outbox (creado_en) WHERE estado = 'PENDIENTE';

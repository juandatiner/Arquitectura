-- DB PAGO (DB SERVER :5432 · Máquina 3)
-- PENDIENTE (VII.10) → PAGADO | RECHAZADO (VII.26)
CREATE TABLE pago (
    numero_compra   VARCHAR(20)   PRIMARY KEY,
    cedula          VARCHAR(20)   NOT NULL,
    valor_ofertado  NUMERIC(14,2) NOT NULL CHECK (valor_ofertado > 0),
    impuestos       NUMERIC(14,2) NOT NULL,
    valor_total     NUMERIC(14,2) NOT NULL,
    estado          VARCHAR(10)   NOT NULL CHECK (estado IN ('PENDIENTE', 'PAGADO', 'RECHAZADO')),
    valor_pagado    NUMERIC(14,2),
    referencia_pago VARCHAR(40),
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    conciliado_en   TIMESTAMPTZ
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

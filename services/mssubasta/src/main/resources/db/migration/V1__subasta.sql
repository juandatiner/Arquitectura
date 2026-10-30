-- DB SUBASTA (DB SERVER :5432 · Máquina 2)

-- PROGRAMADA → ABIERTA (III.14) → EN_CIERRE (V.2) → ADJUDICADA (V.4a) | DESIERTA (V.4b); ADJUDICADA → LIBERADA (X.7a)
CREATE TABLE subasta (
    id_subasta     VARCHAR(20)   PRIMARY KEY,
    id_bien        VARCHAR(20)   NOT NULL,
    descripcion    VARCHAR(300)  NOT NULL,
    valor_minimo   NUMERIC(14,2) NOT NULL CHECK (valor_minimo > 0),
    fecha_inicio   TIMESTAMPTZ   NOT NULL,
    fecha_cierre   TIMESTAMPTZ   NOT NULL CHECK (fecha_cierre > fecha_inicio),
    estado         VARCHAR(12)   NOT NULL
                   CHECK (estado IN ('PROGRAMADA', 'ABIERTA', 'EN_CIERRE', 'ADJUDICADA', 'DESIERTA', 'LIBERADA')),
    -- Control de concurrencia optimista (IV.9, IV.11)
    version        BIGINT        NOT NULL DEFAULT 0,
    cedula_ganador VARCHAR(20),
    valor_ganador  NUMERIC(14,2)
);
CREATE INDEX subasta_estado ON subasta (estado, fecha_cierre);

-- Estado de la oferta (enunciado: "se manejará un estado de la oferta")
CREATE TABLE oferta (
    id_oferta  BIGSERIAL     PRIMARY KEY,
    id_subasta VARCHAR(20)   NOT NULL REFERENCES subasta (id_subasta),
    usuario    VARCHAR(64)   NOT NULL,
    cedula     VARCHAR(20)   NOT NULL,
    nombre     VARCHAR(120),
    celular    VARCHAR(20),
    correo     VARCHAR(120),
    valor      NUMERIC(14,2) NOT NULL CHECK (valor > 0),
    estado     VARCHAR(10)   NOT NULL CHECK (estado IN ('REGISTRADA', 'SUPERADA', 'GANADORA', 'PERDEDORA')),
    creada_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX oferta_mayor ON oferta (id_subasta, valor DESC);

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

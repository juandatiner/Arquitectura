-- DB CATALOGO (DB SERVER :5432 · Máquina 2)

-- Bienes incautados que se subastan
CREATE TABLE bien (
    id_bien      VARCHAR(20)   PRIMARY KEY,
    descripcion  VARCHAR(300)  NOT NULL,
    categoria    VARCHAR(40)   NOT NULL,
    valor_minimo NUMERIC(14,2) NOT NULL CHECK (valor_minimo > 0)
);

-- Subastas del catálogo: PENDIENTE → PUBLICADA (III.9) → LIBERADA (X.7b)
CREATE TABLE subasta (
    id_subasta    VARCHAR(20) PRIMARY KEY,
    id_bien       VARCHAR(20) NOT NULL REFERENCES bien (id_bien),
    fecha_inicio  TIMESTAMPTZ NOT NULL,
    fecha_cierre  TIMESTAMPTZ NOT NULL CHECK (fecha_cierre > fecha_inicio),
    estado        VARCHAR(12) NOT NULL CHECK (estado IN ('PENDIENTE', 'PUBLICADA', 'LIBERADA')),
    publicada_por VARCHAR(64),
    publicada_en  TIMESTAMPTZ
);
CREATE INDEX subasta_estado ON subasta (estado);

-- Outbox transaccional (RepoOutboxCatalogo / WorkerOutboxCatalogo). Ver ADR 0002.
CREATE TABLE outbox (
    id_evento  UUID        PRIMARY KEY,
    tipo       VARCHAR(60) NOT NULL,
    cola       VARCHAR(60),
    payload    JSON        NOT NULL,  -- JSON (no JSONB): conserva el orden de los campos
    estado     VARCHAR(12) NOT NULL CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'REGISTRADO')),
    intentos   INT         NOT NULL DEFAULT 0,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en TIMESTAMPTZ
);
CREATE INDEX outbox_pendientes ON outbox (creado_en) WHERE estado = 'PENDIENTE';

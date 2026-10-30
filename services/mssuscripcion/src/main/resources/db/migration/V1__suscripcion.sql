-- DB Suscripcion (DB SERVER :5432 · Máquina 2)

-- Suscripciones del cliente (II.9b). tipo NUEVAS_SUBASTAS: valor = categoría o TODAS;
-- tipo CAMBIOS_OFERTA: valor = idSubasta.
CREATE TABLE suscripcion (
    id_suscripcion UUID         PRIMARY KEY,
    usuario        VARCHAR(64)  NOT NULL,
    cedula         VARCHAR(20)  NOT NULL,
    nombre         VARCHAR(120),
    correo         VARCHAR(120),
    celular        VARCHAR(20),
    tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('NUEVAS_SUBASTAS', 'CAMBIOS_OFERTA')),
    valor          VARCHAR(40)  NOT NULL,
    estado         VARCHAR(10)  NOT NULL CHECK (estado IN ('ACTIVA', 'CANCELADA')),
    creada_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelada_en   TIMESTAMPTZ
);
CREATE UNIQUE INDEX suscripcion_activa_unica ON suscripcion (cedula, tipo, valor) WHERE estado = 'ACTIVA';
CREATE INDEX suscripcion_busqueda ON suscripcion (tipo, valor) WHERE estado = 'ACTIVA';

-- Subastas conocidas por los eventos recibidos (categoría para SubastaIniciada; estado para X.7c)
CREATE TABLE subasta_conocida (
    id_subasta  VARCHAR(20)  PRIMARY KEY,
    descripcion VARCHAR(300),
    categoria   VARCHAR(40),
    estado      VARCHAR(10)  NOT NULL CHECK (estado IN ('PUBLICADA', 'INICIADA', 'LIBERADA'))
);

-- Consumo idempotente (yaProcesado, III.20 / IV.18)
CREATE TABLE evento_procesado (
    id_evento     UUID        PRIMARY KEY,
    procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
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

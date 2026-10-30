-- DB COMPRA (DB SERVER :5432 · Máquina 2). La comparten las dos réplicas de MSCompra (observación 4).

CREATE SEQUENCE compra_numero;

-- Estados de la saga (docs/arquitectura.md):
-- CREADA → EN_VALIDACION → ESPERANDO_PAGO → PAGO_CONFIRMADO → TERMINADA
--                ↘ CANCELADA (OFAC RECHAZADO)  ↘ CANCELADA (pago rechazado, compensación X)
--                ↘ EN_REVISION (OFAC sin respuesta)           ↘ EN_REVISION (despacho agotado X.9)
CREATE TABLE compra (
    numero_compra          VARCHAR(20)   PRIMARY KEY,
    id_subasta             VARCHAR(20)   NOT NULL UNIQUE,
    id_bien                VARCHAR(20)   NOT NULL,
    descripcion            VARCHAR(300)  NOT NULL,
    usuario                VARCHAR(64)   NOT NULL,
    cedula                 VARCHAR(20)   NOT NULL,
    nombre                 VARCHAR(120),
    celular                VARCHAR(20),
    correo                 VARCHAR(120),
    direccion              VARCHAR(200),
    valor_ofertado         NUMERIC(14,2) NOT NULL CHECK (valor_ofertado > 0),
    estado                 VARCHAR(16)   NOT NULL CHECK (estado IN
                           ('CREADA', 'EN_VALIDACION', 'ESPERANDO_PAGO', 'PAGO_CONFIRMADO', 'TERMINADA', 'CANCELADA', 'EN_REVISION')),
    motivo                 VARCHAR(200),
    -- Validación OFAC con reintentos (VI.22c): intentos, próximo intento y reserva entre réplicas
    intentos_ofac          INT           NOT NULL DEFAULT 0,
    proximo_intento        TIMESTAMPTZ,
    reservada_hasta        TIMESTAMPTZ,
    referencia_pago        VARCHAR(60),
    despacho_solicitado_en TIMESTAMPTZ,
    creada_en              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizada_en         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX compra_cedula ON compra (cedula);
CREATE INDEX compra_validacion ON compra (proximo_intento) WHERE estado = 'EN_VALIDACION';
CREATE INDEX compra_despacho ON compra (despacho_solicitado_en) WHERE estado = 'PAGO_CONFIRMADO';

-- Los eventos recibidos son idempotentes por diseño: existePorSubasta (V.10) y UPDATE condicionales.

-- Outbox transaccional (ADR 0002), compartido por las dos réplicas (FOR UPDATE SKIP LOCKED)
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

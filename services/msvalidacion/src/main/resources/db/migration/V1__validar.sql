-- DB VALIDAR (DB SERVER :5432 · Máquina 2): resultado de la validación OFAC por compra (VI.16, VI.21)
CREATE TABLE resultado_validacion (
    numero_compra  VARCHAR(20) PRIMARY KEY,
    estado         VARCHAR(10) NOT NULL CHECK (estado IN ('APROBADO', 'RECHAZADO', 'ENPROCESO')),
    consultas      INT         NOT NULL DEFAULT 1,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- DB NOTIFICACION (DB SERVER :5432 · Máquina 2): envíos realizados por evento y canal (IX.3, IX.8).
-- Si un canal falla, el mensaje se reintenta y solo se envían los canales que faltan.
CREATE TABLE notificacion_enviada (
    id_evento   UUID         NOT NULL,
    canal       VARCHAR(6)   NOT NULL CHECK (canal IN ('CORREO', 'SMS')),
    resultado   VARCHAR(120) NOT NULL,
    enviada_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id_evento, canal)
);

-- DB YOPAGO (DB SERVER :5432 · Máquina 3) · sistema externo YoPago
-- Usuarios de YoPago: el cliente se identifica con su cédula y contraseña (enunciado).
-- Se cargan al desplegar (tools/carga/cargar_yopago.py); la clave se guarda con hash PBKDF2-SHA256.
CREATE TABLE usuario (
    cedula     VARCHAR(20)  PRIMARY KEY,
    nombre     VARCHAR(120) NOT NULL,
    clave_hash VARCHAR(200) NOT NULL
);

-- Compras SSO pendientes por pago (VII.9) y su pago (VII.18). El reporte a SSO (VII.19) se reintenta hasta lograrlo.
CREATE TABLE compra_pendiente (
    numero_compra    VARCHAR(20)   PRIMARY KEY,
    cedula           VARCHAR(20)   NOT NULL,
    valor_total      NUMERIC(14,2) NOT NULL CHECK (valor_total > 0),
    estado           VARCHAR(10)   NOT NULL CHECK (estado IN ('PENDIENTE', 'PAGADA')),
    medio_pago       VARCHAR(20),
    referencia       VARCHAR(40),
    pagada_en        TIMESTAMPTZ,
    reporte          VARCHAR(12)   CHECK (reporte IN ('PENDIENTE', 'REPORTADO')),
    resultado_sso    VARCHAR(12),
    intentos_reporte INT           NOT NULL DEFAULT 0,
    creada_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX compra_pendiente_cedula ON compra_pendiente (cedula);
CREATE INDEX compra_pendiente_reporte ON compra_pendiente (pagada_en) WHERE reporte = 'PENDIENTE';

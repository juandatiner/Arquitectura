-- DB OFAC (DB SERVER :5432 · Máquina 3): personas reportadas o en estudio.
-- Una cédula que no aparece está habilitada para comprar. Los datos se cargan al desplegar
-- (tools/carga/cargar_ofac.py); el código no trae ninguno.
CREATE TABLE reporte (
    cedula        VARCHAR(20)  PRIMARY KEY,
    estado        VARCHAR(12)  NOT NULL CHECK (estado IN ('REPORTADO', 'EN_ESTUDIO')),
    motivo        VARCHAR(300),
    registrado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);

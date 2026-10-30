-- Solo para OFACBackEndIT
INSERT INTO reporte (cedula, estado, motivo) VALUES
    ('7777777777', 'REPORTADO', 'prueba'),
    ('8888888888', 'EN_ESTUDIO', 'prueba')
ON CONFLICT DO NOTHING;

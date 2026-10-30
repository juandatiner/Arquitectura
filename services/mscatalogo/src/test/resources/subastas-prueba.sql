-- Solo para MSCatalogoIT
INSERT INTO bien (id_bien, descripcion, categoria, valor_minimo)
VALUES ('BIEN-0001', 'Camioneta de prueba', 'VEHICULOS', 95000000) ON CONFLICT DO NOTHING;
INSERT INTO subasta (id_subasta, id_bien, fecha_inicio, fecha_cierre, estado)
VALUES ('SUB-0001', 'BIEN-0001', now() + interval '1 day', now() + interval '4 days', 'PENDIENTE') ON CONFLICT DO NOTHING;

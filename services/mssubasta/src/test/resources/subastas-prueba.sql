-- Solo para MSSubastaIT
INSERT INTO subasta (id_subasta, id_bien, descripcion, valor_minimo, fecha_inicio, fecha_cierre, estado) VALUES
    ('SUB-0001', 'BIEN-0001', 'Camioneta de prueba', 95000000, now() + interval '1 day', now() + interval '4 days', 'PROGRAMADA'),
    ('SUB-0002', 'BIEN-0002', 'Apartamento de prueba', 320000000, now() + interval '1 day', now() + interval '7 days', 'PROGRAMADA')
ON CONFLICT DO NOTHING;

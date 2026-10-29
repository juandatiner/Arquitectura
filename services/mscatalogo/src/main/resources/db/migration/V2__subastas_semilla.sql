-- Subastas pendientes de publicar (observación 9: el diagrama no modela su creación).
-- Mismos id_subasta, id_bien y fechas que DB SUBASTA (MSSubasta). Fechas relativas al día de la migración.
INSERT INTO bien (id_bien, descripcion, categoria, valor_minimo) VALUES
    ('BIEN-0001', 'Camioneta Toyota Prado 2019, blanca',            'VEHICULOS',   95000000),
    ('BIEN-0002', 'Apartamento 78 m² en Chapinero, Bogotá',          'INMUEBLES',  320000000),
    ('BIEN-0003', 'Reloj Rolex Submariner acero',                    'JOYAS',       28000000),
    ('BIEN-0004', 'Lancha con motor fuera de borda 200 HP',           'VEHICULOS',   60000000),
    ('BIEN-0005', 'Lote de 20 computadores portátiles',               'TECNOLOGIA',  18000000);

INSERT INTO subasta (id_subasta, id_bien, fecha_inicio, fecha_cierre, estado) VALUES
    ('SUB-0001', 'BIEN-0001', date_trunc('day', now()) + interval '1 day 8 hours',  date_trunc('day', now()) + interval '4 days 18 hours', 'PENDIENTE'),
    ('SUB-0002', 'BIEN-0002', date_trunc('day', now()) + interval '1 day 8 hours',  date_trunc('day', now()) + interval '7 days 18 hours', 'PENDIENTE'),
    ('SUB-0003', 'BIEN-0003', date_trunc('day', now()) + interval '2 days 8 hours', date_trunc('day', now()) + interval '5 days 18 hours', 'PENDIENTE'),
    ('SUB-0004', 'BIEN-0004', date_trunc('day', now()) + interval '2 days 8 hours', date_trunc('day', now()) + interval '6 days 18 hours', 'PENDIENTE'),
    ('SUB-0005', 'BIEN-0005', date_trunc('day', now()) + interval '3 days 8 hours', date_trunc('day', now()) + interval '8 days 18 hours', 'PENDIENTE');

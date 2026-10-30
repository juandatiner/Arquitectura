-- Mismas subastas que DB CATALOGO (observación 9): mismos id, bien, valor mínimo y fechas.
INSERT INTO subasta (id_subasta, id_bien, descripcion, valor_minimo, fecha_inicio, fecha_cierre, estado) VALUES
    ('SUB-0001', 'BIEN-0001', 'Camioneta Toyota Prado 2019, blanca',  95000000, date_trunc('day', now()) + interval '1 day 8 hours',  date_trunc('day', now()) + interval '4 days 18 hours', 'PROGRAMADA'),
    ('SUB-0002', 'BIEN-0002', 'Apartamento 78 m² en Chapinero, Bogotá', 320000000, date_trunc('day', now()) + interval '1 day 8 hours',  date_trunc('day', now()) + interval '7 days 18 hours', 'PROGRAMADA'),
    ('SUB-0003', 'BIEN-0003', 'Reloj Rolex Submariner acero',          28000000, date_trunc('day', now()) + interval '2 days 8 hours', date_trunc('day', now()) + interval '5 days 18 hours', 'PROGRAMADA'),
    ('SUB-0004', 'BIEN-0004', 'Lancha con motor fuera de borda 200 HP', 60000000, date_trunc('day', now()) + interval '2 days 8 hours', date_trunc('day', now()) + interval '6 days 18 hours', 'PROGRAMADA'),
    ('SUB-0005', 'BIEN-0005', 'Lote de 20 computadores portátiles',     18000000, date_trunc('day', now()) + interval '3 days 8 hours', date_trunc('day', now()) + interval '8 days 18 hours', 'PROGRAMADA');

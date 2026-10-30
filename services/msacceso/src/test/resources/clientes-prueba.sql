-- Solo para MSAccesoIT
INSERT INTO cliente (usuario, cedula, nombre, celular, correo, direccion, rol)
VALUES ('cliente1', '1010101010', 'Ana Gómez', '3001110001', 'cliente1@prueba.local', 'Cra 7 # 40-62', 'CLIENTE')
ON CONFLICT DO NOTHING;

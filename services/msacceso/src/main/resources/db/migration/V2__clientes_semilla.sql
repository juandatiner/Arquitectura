-- Mismos usuarios que deploy/ad/ldif/01-usuarios.ldif
INSERT INTO cliente (usuario, cedula, nombre, celular, correo, direccion, rol) VALUES
    ('cliente1', '1010101010', 'Ana Gómez',     '3001110001', 'cliente1@sso.local', 'Cra 7 # 40-62, Bogotá',  'CLIENTE'),
    ('cliente2', '1020202020', 'Luis Pérez',    '3001110002', 'cliente2@sso.local', 'Cl 45 # 13-20, Bogotá',  'CLIENTE'),
    ('cliente3', '1030303030', 'Marta Ríos',    '3001110003', 'cliente3@sso.local', 'Av 68 # 22-10, Bogotá',  'CLIENTE'),
    ('admin1',   '1090909090', 'Administrador', '3001119999', 'admin1@sso.local',   'Cra 7 # 40-62, Bogotá',  'ADMIN');

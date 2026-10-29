-- DB ACCESO: datos de los clientes autenticados en AD (paso I.11)
CREATE TABLE cliente (
    usuario   VARCHAR(64)  PRIMARY KEY,
    cedula    VARCHAR(20)  NOT NULL UNIQUE,
    nombre    VARCHAR(120) NOT NULL,
    celular   VARCHAR(20)  NOT NULL,
    correo    VARCHAR(120) NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    rol       VARCHAR(10)  NOT NULL CHECK (rol IN ('CLIENTE', 'ADMIN'))
);

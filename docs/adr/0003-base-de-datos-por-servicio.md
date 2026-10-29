# 0003 · Una instancia PostgreSQL por servicio

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
Restricción del enunciado: las bases de datos de todos los sistemas son diferentes y no se conectan directamente entre sí.

## Decisión
Cada servicio tiene su propio contenedor PostgreSQL (puerto 5432 interno, sin publicar al host) en una red Docker privada solo con su servicio. Excepción: DB COMPRA se publica en Máquina 2 para que la réplica 2 de MSCompra (Máquina 3) use la misma base; es el mismo servicio, no otro sistema.
El esquema se versiona con Flyway (Java) y migraciones EF Core (.NET).

## Consecuencias
- Ningún servicio puede leer la base de otro: solo HTTP o eventos.
- Más contenedores en Máquina 2 (8 PostgreSQL); se limita la memoria de cada uno (`shared_buffers=32MB`).

# Architecture Decision Records

Formato [MADR](https://adr.github.io/madr/). Un archivo por decisión, numerado y nunca reescrito: si cambia la decisión se crea un ADR nuevo que reemplaza al anterior.

| # | Decisión | Estado |
|---|----------|--------|
| [0001](0001-monorepo.md) | Monorepo con Maven multi-módulo y solución .NET | Aceptada |
| [0002](0002-outbox-transaccional.md) | Outbox transaccional + consumidores idempotentes | Aceptada |
| [0003](0003-base-de-datos-por-servicio.md) | Una instancia PostgreSQL por servicio | Aceptada |
| [0004](0004-despliegue-docker-por-maquina.md) | Docker Compose por máquina con imágenes amd64 | Aceptada |
| [0005](0005-ad-simulado-con-openldap.md) | Active Directory simulado con OpenLDAP | Aceptada |
| [0006](0006-balanceo-mscompra.md) | Balanceo de MSCompra con Nginx `least_conn` y competencia en colas | Aceptada |

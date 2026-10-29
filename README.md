# SSO · Sistema de Subastas en Línea

Arquitectura integrada **Java EE (Spring Boot) + .NET** para subastar bienes incautados.
Proyecto del curso *Arquitectura de Software* — Pontificia Universidad Javeriana, semestre 2026-30.

> El diseño de referencia está en [`docs/enunciado/ProyectoSSO-Integrada-JavaEE-NET.vpp`](docs/enunciado/ProyectoSSO-Integrada-JavaEE-NET.vpp)
> y los 10 casos de uso (214 pasos) en [`docs/enunciado/casos-de-uso-arquitectura-integrada.pdf`](docs/enunciado/casos-de-uso-arquitectura-integrada.pdf).
> Cada clase del código corresponde 1:1 a una clase del diagrama EBC.

## Vista general

```
                    ┌──────────────── MÁQUINA 1 · 10.43.99.2 (DMZ) ────────────────┐
 Browser / Android ─▶ Nginx :80 ─▶ APIGateway :8080          SSOWeb :80 · YoPagoWeb :5000
                    └───────────────────────────┬──────────────────────────────────┘
                                                │ HTTP (red interna)
 ┌──────────── MÁQUINA 2 · 10.43.100.238 ───────▼───────┐   ┌──────── MÁQUINA 3 · 10.43.99.45 ────────┐
 │ MSAcceso :8081   MSCatalogo :8082  MSSubasta :8083   │   │ MSCompra réplica 2 :8085  MSPago :5086  │
 │ MSSuscripcion :8084  MSCompra réplica 1 :8085        │◀─▶│ OFAC :9001  SIN :9002  SEN :9003        │
 │ MSValidacion :8087  MSDespacho :8088                 │   │ YoPago :5001  AD/LDAP :389              │
 │ MSNotificacion :8089  RabbitMQ :5672  PostgreSQL ×8  │   │ Mail :1025  SMS :9004  PostgreSQL ×6    │
 └──────────────────────────────────────────────────────┘   └─────────────────────────────────────────┘
```

Detalle completo: [`docs/arquitectura.md`](docs/arquitectura.md) · Decisiones: [`docs/adr/`](docs/adr/)

## Stack

| Capa | Tecnología |
|------|------------|
| Microservicios Java | Java 21 · Spring Boot · Spring Data JPA · Spring AMQP · Flyway |
| Microservicios .NET | .NET · ASP.NET Core · EF Core · RabbitMQ.Client |
| Front web | Angular (SPA) servida por Nginx |
| Móvil | Android (Kotlin) |
| Mensajería | RabbitMQ (AMQP) con patrón Outbox transaccional |
| Datos | PostgreSQL — una base por servicio, sin acceso cruzado |
| Identidad | OpenLDAP simulando Active Directory · JWT emitido por MSAcceso |
| Despliegue | Docker Compose por máquina · imágenes `linux/amd64` en GHCR |

## Estructura del repositorio

```
services/        microservicios Java (Maven multi-módulo)
services-dotnet/ MSPago, YoPago y YoPagoWeb (solución .NET)
frontend/        sso-web (Angular) · sso-mobile (Android)
external/        sistemas externos simulados en Java: OFAC, SIN, SEN, SMS
libs/            código compartido (outbox, eventos, idempotencia)
deploy/          docker-compose y .env por máquina
docs/            arquitectura, ADR, enunciado y modelo VPP
```

## Flujo de trabajo

1. Elegir un issue del tablero, asignarse y mover a *In progress*.
2. Rama `tipo/numero-descripcion` (ej. `feat/23-registrar-oferta`).
3. Commits con [Conventional Commits](https://www.conventionalcommits.org/es/).
4. PR con plantilla, `Closes #N`, CI en verde y una revisión aprobada.

Ver [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Despliegue

Se construye todo en la máquina de desarrollo, se publican imágenes y cada VM ejecuta su propio
`deploy/maquina-N/docker-compose.yml`. Pasos en [`deploy/README.md`](deploy/README.md).

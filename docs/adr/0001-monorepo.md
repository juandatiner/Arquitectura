# 0001 · Monorepo con Maven multi-módulo y solución .NET

- Estado: Aceptada
- Fecha: 2026-10-29

## Contexto
El sistema tiene 11 artefactos Java, 3 .NET, una SPA Angular y una app Android. El equipo es pequeño, la entrega es un único `.zip` con los `pom.xml`, y los contratos de eventos se comparten entre servicios.

## Decisión
Un solo repositorio. Java en un `pom.xml` padre (`services/pom.xml`) con un módulo por microservicio y una librería `libs/sso-commons`. .NET en `SSO.Dotnet.sln`. Frontends en `frontend/`.

## Consecuencias
- Un PR puede cambiar productor y consumidor de un evento a la vez.
- CI filtra por rutas para no compilar todo en cada cambio.
- El `.zip` de entrega se genera con un solo comando.

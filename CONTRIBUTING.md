# Guía de contribución

## Ramas
- `main` protegida: solo entra código por PR con CI en verde y una aprobación.
- Nombre: `tipo/numero-descripcion-corta` → `feat/23-registrar-oferta`, `fix/41-timer-cierre`.

## Commits — Conventional Commits
```
feat(mssubasta): registrar oferta con control optimista

Closes #23
```
Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.
Scope = nombre del servicio en minúscula (`apigateway`, `msacceso`, `mscompra`, `mspago`, `sso-web`, `deploy`…).

## Pull requests
- Título en formato Conventional Commits (lo valida CI).
- Un PR = un issue. Incluir `Closes #N`.
- Tamaño objetivo: < 400 líneas cambiadas.
- Completar la plantilla: qué cambia, pasos del caso de uso cubiertos (ej. `IV.9–IV.13a`), cómo probar.

## Definición de terminado
- [ ] Clases, operaciones, parámetros, puertos y colas idénticos al diagrama «Arquitectura Integrada JavaEE-.NET» (ver `CLAUDE.md`).
- [ ] `python3 -I tools/vpp/verificar_codigo.py` termina en ✓.
- [ ] Cada método documenta los pasos del caso de uso que implementa (`/** Pasos IV.9, IV.11 */`).
- [ ] Sin lógica de negocio en front end.
- [ ] Pruebas unitarias del `Srv*` y prueba de integración con Testcontainers del flujo principal.
- [ ] `Dockerfile` construye y el servicio arranca en el `docker compose` de su máquina.
- [ ] Health check `/actuator/health` (Java) o `/health` (.NET).
- [ ] Documentación de endpoints (OpenAPI) y eventos actualizada.

## Estilo
- Java: Spotless (google-java-format), Checkstyle, Java 21.
- .NET 8: `dotnet format`, analizadores habilitados, `Nullable` activado.
- Angular: ESLint + Prettier.
- Paquete Java raíz `co.edu.javeriana.sso.<componente>` = solo las clases del diagrama + `<Componente>Application`. Soporte en `.modelo` (entidades, DTO, eventos, enums) y `.config`.

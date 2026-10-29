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

## Entorno local

- Java 21 (`JAVA_HOME`), Docker. No hace falta Maven: `services/mvnw`.
- Pruebas: `cd services && ./mvnw verify` (unitarias, ArchUnit contra el diagrama e integración con Testcontainers).
- Si Docker corre en **Colima** (Mac), Testcontainers necesita:
  ```bash
  export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```
- Entorno completo: `tools/scripts/generar-claves-token.sh deploy/secretos` y `docker compose -f deploy/dev/docker-compose.yml up -d --build`.
  Si un puerto del host está ocupado, se cambia con la variable `<SERVICIO>_PUERTO_HOST` (solo en desarrollo; en las VM se usan los puertos del diagrama).
- Imágenes para las VM Ubuntu: siempre `--platform linux/amd64`.

## Estilo
- Java: Spotless (google-java-format), Checkstyle, Java 21.
- .NET 8: `dotnet format`, analizadores habilitados, `Nullable` activado.
- Angular: ESLint + Prettier.
- Paquete Java raíz `co.edu.javeriana.sso.<componente>` = solo las clases del diagrama + `<Componente>Application`. Soporte en `.modelo` (entidades, DTO, eventos, enums) y `.config`.

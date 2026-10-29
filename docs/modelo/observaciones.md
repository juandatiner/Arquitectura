# Observaciones sobre el modelo

Puntos donde el diagrama «Arquitectura Integrada JavaEE-.NET» o el PDF de casos de uso es ambiguo o
parece tener un error. **No se corrigen en el código por cuenta propia**: se decide con el equipo y,
si cambia el modelo, se actualiza primero el VPP y luego se regenera `modelo-canonico.json`.

| # | Paso / elemento | Observación | Decisión |
|---|-----------------|-------------|----------|
| 1 | VI.22c / VI.23c | El PDF dice `SrvCompraSaga → RepoOutboxCompra cambiarEstado(EN_REVISION) · guardar(NotificacionDemora)`. `cambiarEstado` es de `RepoCompra` (VI.22b, X.1) y el pliego solo muestra el número del paso sobre cada flecha. | **Decidida (2026-10-29):** `VI.22c` → `RepoCompra.registrarIntento(intento + 1) · cambiarEstado(EN_REVISION)` (solo al agotar intentos); `VI.23c` → `RepoOutboxCompra.guardar(NotificacionDemora)`, en la misma transacción. Las flechas del pliego no cambian. Aplicada en `CORRECCIONES` de `extraer_modelo.py`. Conviene corregir el texto en el PDF de casos de uso. |
| 2 | VII.32b | Autollamada `SrvCompraSaga → SrvCompraSaga compensar(...)`; no tiene flecha en el despliegue (es interna). | Se implementa como método de `SrvCompraSaga` |
| 3 | Máquina 1 | `BALANCEADOR DE CARGAS :80` y `SSOWeb SERVER :80` usan el mismo puerto en la misma máquina. | **Decidida (2026-10-29):** en Docker ambos escuchan :80 dentro de su contenedor; solo el balanceador publica :80 al host. El balanceador sirve `/` reenviando a SSOWeb por la red interna de Docker y `/api/` al APIGateway. |
| 4 | DB COMPRA | Las dos réplicas de `MSCompra` (Máquinas 2 y 3) tienen flecha JDBC a `DB COMPRA`, que está en Máquina 2. | La réplica 2 se conecta por red a la base de Máquina 2 |
| 5 | SMS SERVER, MAIL SERVER, AD | Nodos sin componente propio (sistemas externos). | Se simulan con contenedores: OpenLDAP (estereotipo del diagrama), Mailpit (SMTP :1025) y un simulador HTTP :9004 |
| 6 | VI.1 `consultarMisCompras()` | La pantalla consulta las compras del cliente (VI.1), pero ningún paso lleva esa consulta al backend (VI.2 – VI.10 solo cubren `iniciarCompra`). | **Decidida (2026-10-29):** se usa el mismo camino de flechas de VI.4 – VI.8, sin flechas nuevas: `WSGateway.consultarMisCompras` → `SrvSeguridadToken.validarYAutorizar` → `SrvRuteo.enrutarConsultaCompras` → Nginx (upstream msCompra) → `WSCompra`. |
| 7 | II.7b `cancelarSuscripcion` | El diagrama enruta suscribirse y cancelar por la misma operación `enrutarSuscripcion`. | `SrvRuteo.enrutarSuscripcion(solicitud, cliente)` atiende ambas variantes; `WSGateway` expone `DELETE /api/suscripciones/{id}`. |
| 8 | X.8b / X.8a / X.8c `BienLiberadoConfirmado` | Se guarda en el outbox de Catálogo, Subasta y Suscripción, pero el diagrama no le dibuja flecha hacia ninguna cola ni consumidor. | Se guarda en el outbox con estado `REGISTRADO` (constancia auditable de la compensación) y el worker no lo publica. |
| 9 | Creación de subastas | El diagrama parte de subastas ya existentes: `publicarSiPendienteYEnFecha` (III.9) en DB CATALOGO y `abrirSiProgramadaYEnPlazo` (III.14) en DB SUBASTA, pero ningún paso las crea. | Datos semilla (Flyway) idénticos en DB CATALOGO y DB SUBASTA: mismos `id_subasta`, `id_bien`, valor mínimo y fechas (relativas al día de la migración). |

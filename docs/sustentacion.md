# Guion de sustentación y demo en vivo

Basado en lo que existe en el repositorio. Lo que nadie ha comprobado o que debe decidir el equipo está marcado **PENDIENTE (Pn)** y
reunido en la [sección 12](#12-lista-de-pendientes).

Datos, claves e identificadores van entre `<...>`: nada de eso va en el repositorio (`CLAUDE.md`, regla 3b).

## 1. Estado real antes de sustentar

| Qué | Estado | Dónde consta |
|---|---|---|
| `SSOWeb.zip` (Angular) | En PR abierto, sin fusionar | #139 |
| `deploy/maquina-N/docker-compose.yml` | En PR borrador, **nunca levantado** | #142 |
| Liberar bien: observación 16, pruebas de X.6–X.8 y Javadoc de `RepoCatalogo` | En PR abierto, sin fusionar | #141 |
| `Makefile`, `make zip` y `tools/scripts/smoke-test.sh` | En PR borrador, sin fusionar | #143 |
| E2E completo, `tools/scripts/e2e.sh` | En curso, aún sin PR | #65 |
| Comprobación de red, `tools/scripts/check-red.sh` | En curso, aún sin PR | #68 |
| Escenarios de falla con evidencia | Sin hacer | #66 |
| Recorrido en Chrome real, CU I a VI | Hecho contra el stack local en Docker | Descripción de #139 |
| CU VII a X en pantalla, de punta a punta | Sin comprobar. Solo pruebas de integración por servicio (ej. `MSCompraIT`) | **PENDIENTE (P12)** |
| Las tres VM | Nada desplegado | **PENDIENTE (P4)**, #68 |

Los comandos de las secciones 3, 4, 6 y 7 usan `deploy/maquina-N/docker-compose.yml` y la estructura `~/sso/` de **#142**. No sirven hasta
que ese PR entre o se copie su contenido. En esta rama `deploy/maquina-N/` solo trae `.env.example`.

## 2. Reparto de la nota

Literal de `docs/enunciado/enunciado-proyecto-codificacion.docx` («Proyecto 2: 20 %»):

| Parte | Peso | Qué se entrega | Dónde está | Estado |
|---|---|---|---|---|
| Arquitectura Integrada JavaEE y .NET | 30 % | Impreso en papel gigante y VPP en BS (10 p. m.) | `docs/enunciado/arquitectura-integrada-pliego-100x70.pdf`, `ProyectoSSO-Integrada-JavaEE-NET*.vpp` | **PENDIENTE (P18)**, #73 |
| Codificación | 60 % | `.zip` en BS, con los `pom.xml`. Spring Boot salvo lo marcado .NET | `services/pom.xml`, `services-dotnet/SSO.Dotnet.sln`, `frontend/sso-web` | **PENDIENTE (P19)**, #74 |
| Aporte grupal | 10 % | Motor Docker en cada máquina y dos microservicios en máquinas distintas comunicados | `docs/aporte-grupal.md` (en #142, evidencia vacía) | **PENDIENTE (P20)**, #72 |

El enunciado deja en blanco las fechas de entrega y sustentación: **PENDIENTE (P17)**. También marca «(10) se pide escalabilidad y balanceo del
servicio de compra» dentro de la parte de código, sin aclarar a qué peso corresponde ese 10.

Exigencias del enunciado que el profesor puede pedir ver:

- Colores por sistema y por caso de uso en los diagramas, incluidos los de secuencia alternativos.
- IP de las máquinas en la arquitectura.
- Servicios fuera del IDE.
- «Se deben usar todas las máquinas de todos los usuarios del grupo»: **PENDIENTE (P22)**.

## 3. Arranque en las VM: M3 → M2 → M1

| Orden | Máquina | IP | Qué levanta |
|---|---|---|---|
| 1 | Máquina 3 | 10.43.99.45 | AD, Mail, SMS, MSCompra réplica 2, OFAC, SIN, SEN, MSPago, YoPago y sus 5 bases |
| 2 | Máquina 2 | 10.43.100.238 | RabbitMQ, MSAcceso, MSCatalogo, MSSubasta, MSSuscripcion, MSCompra réplica 1, MSValidacion, MSDespacho, MSNotificacion y sus 8 bases |
| 3 | Máquina 1 (DMZ) | 10.43.99.2 | Nginx, SSOWeb, APIGateway, YoPagoFrontEnd |

Preparación previa (imágenes, secretos por máquina, firewall, `docker compose pull`): `deploy/README.md` de #142.

En cada VM, en el orden de la tabla:

```bash
cd ~/sso/deploy/maquina-N
docker compose up -d --no-build
docker compose ps
```

Compose no espera a otra máquina. Al levantar M3 primero, `mscompra-replica2` se reinicia hasta que DB COMPRA de M2 responda
(`restart: unless-stopped`). Los demás servicios de M3 que usan RabbitMQ también dependen de M2. Por eso M3 no se juzga completa hasta que M2 esté
arriba: el `docker compose ps` que vale es el final, en las tres máquinas.

Antes de empezar se comprueba que la hora de M2 esté bien (`date`): la apertura y el cierre de las subastas dependen de ella.

Al terminar, desde la máquina de desarrollo y solo con #143 fusionado: `make smoke MAQUINA1_IP=10.43.99.2`. Es de solo lectura y comprueba la entrada pública; con
`SMOKE_USUARIO` y `SMOKE_CLAVE`, también login, catálogo y mis compras. No recorre el flujo I a X. Según #143, aún no se ha probado contra el sistema real.

**PENDIENTE (P4):** este orden nunca se ha ejecutado. **PENDIENTE (P5):** faltan valores de `.env` sin definir, como `MEMORIA_*` y `TASA_IMPUESTO`.

## 4. Carga de datos con `tools/carga`

El repositorio solo trae plantillas con encabezados (`tools/carga/plantillas/`). Los CSV viven en la VM. Se ejecuta una vez, con las tres máquinas
arriba, porque las tablas las crean las migraciones de cada servicio.

### 4.1 Filas que necesita el guion

| Archivo | Filas necesarias | Condiciones |
|---|---|---|
| `usuarios.csv` | Un administrador (`ADMIN`) y tres clientes: A, B y C (`CLIENTE`) | Clave de 8 o más caracteres. Celular, correo y dirección válidos: los avisos llegan a los simuladores |
| `yopago.csv` | Cliente A | **Misma cédula** que en `usuarios.csv`. La clave de YoPago es otra, independiente de la de SSO |
| `ofac.csv` | Cliente C con estado `EN_ESTUDIO` | Quien no aparece en la lista sale `APROBADO` |
| `subastas.csv` | S1, S2 y S3 | Fechas ISO-8601 **con zona**. Una misma categoría en MAYÚSCULAS, la que suscribe el cliente A en CU II |

Las fechas de `subastas.csv` se calculan el mismo día:

- `fecha_inicio` debe quedar **después** del momento en que el administrador publica. Así el aviso «Subasta abierta» encuentra la categoría que
  llegó con `NuevaSubasta`.
- `fecha_cierre` debe dejar tiempo para ofertar con calma, pero no tanto como para esperar en silencio frente al profesor.
- Los tiempos concretos se calibran en el ensayo: **PENDIENTE (P10)**.

### 4.2 Comandos

```bash
# Máquina 3
cd ~/sso/deploy/maquina-3
AD_CONTENEDOR=$(docker compose ps -q ad) AD_CLAVE_ADMIN=<AD_ADMIN_CLAVE> \
  python3 -I ../../tools/carga/cargar_usuarios.py <ruta>/usuarios.csv --destino ad
DB_YOPAGO_CONTENEDOR=$(docker compose ps -q db-yopago) python3 -I ../../tools/carga/cargar_yopago.py <ruta>/yopago.csv
DB_OFAC_CONTENEDOR=$(docker compose ps -q db-ofac) python3 -I ../../tools/carga/cargar_ofac.py <ruta>/ofac.csv

# Máquina 2
cd ~/sso/deploy/maquina-2
DB_ACCESO_CONTENEDOR=$(docker compose ps -q db-acceso) \
  python3 -I ../../tools/carga/cargar_usuarios.py <ruta>/usuarios.csv --destino acceso
DB_CATALOGO_CONTENEDOR=$(docker compose ps -q db-catalogo) DB_SUBASTA_CONTENEDOR=$(docker compose ps -q db-subasta) \
  python3 -I ../../tools/carga/cargar_subastas.py <ruta>/subastas.csv
```

Los scripts validan todo antes de cargar y son idempotentes. **PENDIENTE (P7):** no se han corrido en las VM, y requieren `python3` allí.

### 4.3 Dos reglas que hay que recordar

**El administrador debe publicar.** `cargar_subastas.py` deja la subasta `PENDIENTE` en DB CATALOGO y `PROGRAMADA` en DB SUBASTA. El catálogo solo
lista `PUBLICADA`, así que sin publicar no sale en pantalla. Además MSSubasta abre y cierra por sus timers aunque nadie la haya publicado: si se
olvida ese paso, la subasta corre invisible y termina `DESIERTA`.

**Una subasta `LIBERADA` no vuelve.** Tras la compensación queda `LIBERADA`, sale del catálogo y no admite ofertas. Para volver a ofertar el bien se
carga una **subasta nueva del mismo `id_bien`** y el administrador la publica. Es decisión del usuario, en la observación 16 de #141, aún sin fusionar:

- `id_subasta` siempre nuevo: `cargar_subastas.py` no modifica una subasta que ya existe, y `compra.id_subasta` es único.
- Mismos descripción, categoría y valor mínimo: DB CATALOGO conserva los del bien ya cargado, y DB SUBASTA toma los del CSV.
- Un bien liberado nunca llegó a despacharse, así que repetir su `id_bien` no choca en DB SIN. Es distinto al repetir el ensayo con un bien de una compra
  `TERMINADA`: ahí se necesita `id_bien` nuevo, porque `salida_inventario.id_bien` es único.

**PENDIENTE (P11):** cargar esa subasta nueva en vivo no se ha probado; #141 cubre con prueba de integración la parte de Catálogo.

## 5. Recorrido por casos de uso en la web

Pantallas:

- SSO: `http://10.43.99.2/`
- YoPago: `http://10.43.99.2:5000/`

Se abre una pestaña por usuario.

Cuidados que vienen del código:

- **No recargar la página.** La sesión y el carrito viven en memoria: F5 cierra la sesión. El token dura 30 minutos.
- **La columna Estado del catálogo no cambia con la apertura ni el cierre.** Muestra `PUBLICADA` también para una subasta abierta o ya cerrada,
  porque el diagrama no le envía `SubastaCerrada` a Catálogo (observación 10). Quien oferte fuera de plazo recibe el error del backend.
- **Las pantallas no se refrescan solas.** Para ver un cambio de estado se vuelve a pulsar «Mis compras» o «Buscar».
- **«Mis suscripciones» solo muestra las de la sesión actual.** La pantalla no las consulta al entrar.
- **Filas viejas.** El catálogo ordena por fecha de cierre, así que lo que quede de un ensayo sale primero. Se filtra por texto, o se limpia y se
  carga de nuevo antes de sustentar (`docker compose down -v` en cada VM **borra todos los datos**).

### 5.1 Orden de la demo

| CU | Color | Quién | En pantalla | Resultado visible | Dónde mostrarlo |
|---|---|---|---|---|---|
| I · Autenticar | Verde | Cliente A | Clave incorrecta y luego correcta en «Ingresar» | Error; después el catálogo con el nombre del usuario | M3: AD. M2: MSAcceso |
| II · Catálogo y suscribirse | Naranja | Cliente A | Escribir la categoría en «Categoría», «Buscar», «Avisarme de nuevas subastas de ...» | «Suscripción activa»; el catálogo aún sin S1 | M2: MSCatalogo, MSSuscripcion |
| III · Publicar y abrir | Morado | Admin | «Publicar», id de S1; repetir con S2 y S3. Cliente A: «Buscar» | «Subasta ... publicada»; aparecen en el catálogo. Correo «Nueva subasta» y, al llegar `fecha_inicio`, «Subasta abierta» | M2: outbox → `q.nuevaSubasta`, `q.subastaIniciada` |
| IV · Ofertar | Fucsia | A, B y C | Ver 5.2 | «Oferta registrada. Le llegará un mensaje al celular...»; error bajo el mínimo | M2: `q.cambioOfertaMayor`. M3: SMS |
| V · Cerrar y crear compra | Verde azulado | Sistema | Esperar `fecha_cierre` (timer cada 15 s); «Mis compras» | Compra en `CREADA`; correo «Ganó la subasta» | M2: `q.subastaCerrada` |
| VI · Iniciar y validar OFAC | Mostaza | Cliente A | «Continuar compra» en S1, luego «Mis compras» otra vez | `EN_VALIDACION` → `ESPERANDO_PAGO` (timer cada 10 s) | M1: log de Nginx con la réplica. M2: MSValidacion. M3: OFAC |
| VII · Pagar (.NET) | Azul oscuro | Cliente A | Abrir la URL del SMS; cédula y clave; «Ver mis compras»; titular, medio y «Pagar» | «Pago realizado» y «SSO recibió su pago (APROBADO)»; en SSO `PAGO_CONFIRMADO` | M1: YoPagoFrontEnd. M3: YoPago, MSPago |
| VIII · Despachar | Oliva | Sistema | «Mis compras» | `TERMINADA` | M3: tablas de SIN y SEN (5.3) |
| IX · Notificar | Azul | Sistema | Sin pantalla | Correos y SMS de todo el recorrido | M3: SMS y correo. M2: `notificacion_enviada` |
| X · Compensar | Café | Ver 7.3 | «Mis compras» con S2 | `CANCELADA · pago rechazado`; S2 sale del catálogo | M2: las tres bases |

El SMS de VII trae lo que pide el enunciado: valor total (oferta más impuestos, con `TASA_IMPUESTO`) y URL de pago. Se construye con `http://<IP M1>:5000/?compra=<numero>`, y
YoPago resalta esa compra.

### 5.2 Detalle de CU IV

Es el punto donde se ven más reglas del enunciado:

1. Cliente A pulsa «Seguir» en S1 (`CAMBIOS_OFERTA`).
2. Cliente A pulsa «Ofertar» en S1 y en S2 y va a «Mis ofertas»: es el carrito, con **varios bienes**. Prueba un valor bajo el mínimo en S1 y se ve el
   error del backend.
3. Cliente B oferta en S1 un valor válido. A recibe **SMS** «Nueva oferta mayor»: quien oferta no se avisa a sí mismo.
4. Cliente A oferta más en S1 y S2 («Enviar ofertas»), y queda como mayor oferente de ambas.
5. Cliente C oferta en S3 y queda como único oferente (se usa en 7.4).

Una oferta que no supera la mayor también se rechaza (`NO_SUPERA_OFERTA_MAYOR`).

Si se oferta antes de `fecha_inicio`, el backend responde que la subasta no está abierta. Con la subasta recién publicada eso es lo normal.

### 5.3 Comandos de apoyo

Se muestran en un terminal aparte, por máquina. Se ejecutan desde `~/sso/deploy/maquina-N`:

```bash
# M1: qué réplica atendió cada petición de compra (campo upstream=)
docker compose logs -f balanceador | grep compras

# M2: estado de las compras, colas y notificaciones
docker compose exec db-compra psql -U compra -d compra -c \
  "SELECT numero_compra, id_subasta, estado, motivo, intentos_ofac FROM compra ORDER BY creada_en DESC LIMIT 5"
docker compose exec mom rabbitmqctl list_queues name messages consumers
docker compose exec db-notificacion psql -U notificacion -d notificacion -c "SELECT * FROM notificacion_enviada"

# M3: SMS recibidos y registros de SIN y SEN
curl -s http://localhost:9004/sms
docker compose exec db-sin psql -U sin -d sin -c "SELECT * FROM salida_inventario"
docker compose exec db-sen psql -U sen -d sen -c "SELECT * FROM envio"
```

**PENDIENTE (P8): el correo aún no se puede mostrar en las VM.** Afecta a III, V y IX: el enunciado pide «correos a los ganadores», y ese aviso solo va por
correo. La recomendación es un **túnel SSH a :8025, por confirmar**. El túnel no basta por sí solo: `deploy/maquina-3/docker-compose.yml` de #142 no publica
ese puerto en el host, así que `ssh -L 8025:localhost:8025` no llegaría a Mailpit. Hace falta una de estas dos cosas, ninguna probada:

- Publicar el puerto solo en local (`127.0.0.1:8025:8025`) en ese compose. Es un cambio en #142.
- Apuntar el túnel a la IP del contenedor `mail` en M3, sin cambiar el compose.

Mientras no se confirme, la única evidencia en las VM es `notificacion_enviada`.

**PENDIENTE (P16):** ninguno de estos comandos se ha probado en las VM.

## 6. Qué mostrar en cada máquina

| Máquina | Qué abrir | Qué decir |
|---|---|---|
| M1 · 10.43.99.2 | `docker compose ps`; log del balanceador | Solo presentación y entrada. Sin bases de datos. El APIGateway valida el token y enruta, sin negocio |
| M2 · 10.43.100.238 | `docker compose ps`; `rabbitmqctl list_queues`; `list_connections` con la IP de M3 como origen | Núcleo de negocio y MOM. Una base por servicio |
| M3 · 10.43.99.45 | `docker compose ps`; `curl .../sms`; tablas de SIN y SEN | Réplica de compra, .NET y sistemas externos e internos |

Para el aporte grupal (10 %), los comandos están en `docs/aporte-grupal.md` de #142: `MSValidacion` (M2) → `OFACBackEnd` (M3) por HTTP, y
`MSCompra` réplica 2 (M3) → RabbitMQ (M2) por AMQP.

## 7. Tolerancia a fallas

Se hace después del recorrido feliz, con S2 (cliente A, sin pagar) y S3 (cliente C). **PENDIENTE (P14):** ninguno de estos escenarios se ha ejecutado
con el sistema completo (#66). Hay pruebas de integración para OFAC caído y pago rechazado en `MSCompraIT`.

### 7.1 Réplica de MSCompra apagada

```bash
# M1, terminal aparte
docker compose logs -f balanceador | grep compras
```

1. Pulsar «Mis compras» varias veces: el campo `upstream=` debe mostrar las dos réplicas (`10.43.100.238:8085` y `10.43.99.45:8085`). Sin peticiones
   simultáneas, `least_conn` empata y reparte por turnos.
2. En M3: `docker compose stop mscompra-replica2`.
3. «Mis compras» sigue respondiendo y `upstream=` solo muestra M2.
4. En M2: `docker compose exec mom rabbitmqctl list_queues name consumers`. `q.subastaCerrada`, `q.pagoConfirmado` y `q.despachoConfirmado` deben
   bajar de consumidores: la réplica 1 las atiende sola.
5. Se deja apagada para el escenario siguiente: los timers de la réplica 1 también bastan.

Con `proxy_next_upstream` la conexión rechazada se reintenta en la otra réplica. Nginx no reenvía un POST cuya petición ya alcanzó a enviarse, así que una
caída justo a mitad de un `iniciarCompra` puede devolver error al cliente. Pulsar otra vez es seguro: `iniciarCompra` es idempotente en `EN_VALIDACION`.

### 7.2 OFAC caído

1. En M3: `docker compose stop ofacbackend`.
2. Cliente A pulsa «Continuar compra» en S2. La pantalla responde al instante «Estamos validando su compra...»: **el cliente no espera a la OFAC**.
3. «Mis compras» sigue en `EN_VALIDACION`. En M2 sube `intentos_ofac` (5.3); en el log de MSCompra aparece «Validación de ... sin respuesta».
4. En M3: `docker compose start ofacbackend`.
5. En el siguiente intento pasa a `ESPERANDO_PAGO` y llega el SMS con el valor y la URL.
6. En M3: `docker compose start mscompra-replica2`.

Tiempos, según `services/mscompra/src/main/resources/application.yml`:

- Espera entre intentos: 15 s, 30 s, 60 s, 120 s, 240 s, 480 s y 600 s (tope de 10 minutos).
- Máximo 8 intentos: unos **26 minutos** hasta pasar a `EN_REVISION`, con SMS de demora.
- Conviene dejar OFAC caída **alrededor de un minuto**: cuanto más tiempo, más larga la siguiente espera. Se calibra en el ensayo.

`EN_REVISION` no se puede esperar en vivo. Se muestra la prueba `ofacCaidoReintentaYAlAgotarIntentosQuedaEnRevision` en `MSCompraIT`.

### 7.3 Compensación (CU X)

**No hay botón en pantalla para esto.** YoPago siempre reporta el valor de la compra y la cédula correcta, así que por la web la conciliación siempre
aprueba. `SrvPago.conciliarPago` solo rechaza si el valor o la cédula no coinciden con lo pendiente. Se provoca enviando por la misma ruta de VII.20
un reporte con un valor distinto, como si YoPago reportara mal. Requiere S2 en `ESPERANDO_PAGO`.

```bash
# M3: la credencial no se imprime en pantalla
cd ~/sso/deploy/maquina-3
CRED=$(grep '^CREDENCIAL_YOPAGO=' .env | cut -d= -f2-)
curl --cacert ../secretos/balanceador.crt -H 'Content-Type: application/json' \
  -d "{\"cedula\":\"<cedula-A>\",\"numeroCompra\":\"<numero-S2>\",\"valorPagado\":<valor-distinto>,\"credencial\":\"$CRED\"}" \
  https://10.43.99.2/api/pagos/reporte
```

Se espera:

- Respuesta con `RECHAZADO`.
- «Mis compras» de A: `CANCELADA · pago rechazado`.
- S2 fuera del catálogo.
- `LIBERADA` en las tres bases:

```bash
# M2
docker compose exec db-subasta psql -U subasta -d subasta -c "SELECT id_subasta, estado FROM subasta"
docker compose exec db-catalogo psql -U catalogo -d catalogo -c "SELECT id_subasta, estado FROM subasta"
docker compose exec db-suscripcion psql -U suscripcion -d suscripcion -c "SELECT id_subasta, estado FROM subasta_conocida"
```

Advertencias:

- No se debe pagar S2 en YoPago después: allá sigue `PENDIENTE`, y el pago quedaría hecho sobre una compra cancelada. El diagrama no tiene paso de
  devolución.
- La compensación no notifica al cliente: X.1 a X.8 no tienen aviso. El estado se ve en «Mis compras».

**PENDIENTE (P15):** este comando nunca se ha ejecutado contra el sistema completo. `pagoRechazadoCompensaYLiberaElBien` prueba solo desde la cola.

### 7.4 ENPROCESO y RECHAZADO (si hay tiempo)

Con S3, cliente C (`EN_ESTUDIO` en `ofac.csv`):

1. C pulsa «Continuar compra»: OFAC responde `ENPROCESO`, la compra sigue `EN_VALIDACION` y sube `intentos_ofac`.
2. En M3, cambiar a C a `REPORTADO` en `ofac.csv` y volver a correr `cargar_ofac.py` (actualiza el estado de esa cédula).
3. En el siguiente intento: `CANCELADA · RECHAZADO OFAC`, con SMS. El bien no se libera (VI.22b y VI.23b solo cancelan y avisan).

No hay script para pasar de `EN_ESTUDIO` a `APROBADO`: `cargar_ofac.py` no borra filas.

## 8. Alcance: solo web

El sistema se entrega solo con cliente web (Angular). El cliente móvil no se programa, por decisión del usuario (observación 15). `SSOMobile.apk` sigue dibujado porque el diagrama no se modificó, y el verificador lo excluye. Los SMS al celular sí están implementados (CU IX): son la notificación que pide el enunciado.

## 9. Preguntas probables

**¿Cómo se balancea la compra? ¿Por qué `least_conn`?**
Dos réplicas de MSCompra (M2 y M3). Por HTTP, `SrvRuteo` llama al Nginx de M1, que usa `upstream msCompra { least_conn; }`: envía la petición a la réplica con
menos conexiones activas. El diagrama lo fija en VI.7. Por colas, ambas réplicas consumen `q.subastaCerrada`, `q.pagoConfirmado` y `q.despachoConfirmado`, y RabbitMQ
entrega cada mensaje a una sola. Las réplicas no guardan estado en memoria: todo vive en DB COMPRA, con `cambiarEstadoSiActual` (UPDATE condicional). Los timers
usan `SKIP LOCKED` y una reserva de 60 s para no validar dos veces la misma compra. Fuente: ADR 0006.

**¿Qué es el outbox y qué resuelve?**
Si se publica en RabbitMQ después del `commit` y el broker cae en medio, el evento se pierde. Por eso el servicio guarda el cambio de negocio y el evento en la
misma transacción. `WorkerOutbox*` toma lotes cada segundo (`FOR UPDATE SKIP LOCKED`), publica con *publisher confirms* y marca como enviado. La entrega es al menos
una vez, y los consumidores descartan duplicados (`yaProcesado`, UPDATE condicionales). Tras 5 entregas fallidas, el mensaje va a `<cola>.dlq`. Fuente: ADR 0002.

**¿Qué pasa con `ENPROCESO`?**
`SrvValidacionOFAC`: sin reporte → `APROBADO`; `REPORTADO` → `RECHAZADO`; `EN_ESTUDIO` → `ENPROCESO`. MSValidacion guarda el resultado y no repite consultas definitivas;
`ENPROCESO` sí se vuelve a consultar. MSCompra trata `ENPROCESO` y «sin respuesta» igual (VI.22c): reintenta con espera creciente y, al octavo intento, pasa a `EN_REVISION`
con `NotificacionDemora`. El cliente nunca espera en línea: valida `TimerValidacionOFAC` cada 10 s. Sobre `EN_REVISION` no hay pantalla de asesor: el diagrama no la dibuja.

**¿Y si OFAC cae?**
`IntegraOFAC` tiene tiempo máximo; MSValidacion responde 503. `IntegraValidacion` lo trata como «sin respuesta», con un circuito que se abre tras 5 fallas de las últimas
10 llamadas y deja de llamar 30 s. La compra queda en `EN_VALIDACION` y se reintenta.

**¿Cómo funciona la compensación?**
Saga orquestada por `SrvCompraSaga`. Si el pago sale rechazado (VII.32b): `ESPERANDO_PAGO → CANCELADA` (X.1) y `BienLiberado` al outbox (X.2). `q.LiberarBienSubasta` es un exchange
fanout con tres colas (Subasta, Catálogo, Suscripción), y cada una libera con un UPDATE condicional (X.7), así que es idempotente. `BienLiberadoConfirmado` queda en el outbox como
constancia, sin publicarse (observación 8). Un despacho sin confirmar en 30 minutos pasa a `EN_REVISION` **sin** liberar el bien (X.9, X.10, observación 12).

**¿Por qué OFAC rechazado no libera el bien?**
Porque el diagrama no lo dibuja: VI.22b y VI.23b solo cancelan y notifican. `BienLiberado` únicamente sale de `compensar`.

**¿Un bien liberado vuelve a subastarse?**
No por sí solo. Queda `LIBERADA`, sin catálogo ni ofertas. Se oferta de nuevo con una subasta nueva del mismo `id_bien`, cargada con `cargar_subastas.py` y publicada
por el administrador. El diagrama no tiene pasos de reapertura y no se añadieron (observación 16, #141).

**¿Cómo se garantizan bases separadas?**
Una instancia PostgreSQL por servicio: 8 en M2 y 5 en M3, cada una en una red Docker privada con su servicio, sin publicar al host. La excepción es DB COMPRA, publicada en M2 para la
réplica 2: es el mismo servicio, no otro sistema (ADR 0003, observación 4). Entre sistemas solo hay HTTP o eventos. Por eso el token lleva cédula, celular, correo y dirección: las
compras no leen DB ACCESO (observación 14).

**¿Cómo se aísla la web? ¿Qué hay en la DMZ?**
En M1 solo están Nginx, SSOWeb, APIGateway y YoPagoFrontEnd, sin bases de datos. El APIGateway valida el JWT con la clave pública (la privada queda en M2) y enruta; nunca toma la identidad
del cuerpo de la petición. Publica 80, 443 y 5000; Nginx rechaza `/api/pagos/reporte` por HTTP con 403, porque VII.20 exige HTTPS. **PENDIENTE (P6):** el aislamiento de red real depende del
firewall de las VM (#68), que no se ha configurado. M1 (10.43.99.2) y M3 (10.43.99.45) comparten prefijo, y no se ha comprobado cómo está segmentada la red.

**¿Dónde está la lógica de negocio del front?**
No hay. `PantallaWebSubasta` y `PantallaWebYoPago` solo presentan y llaman a su `Proxy*`. El carrito es estado de pantalla: ofertar por varios bienes es un `registrarOferta` por cada uno,
y el backend decide todas las reglas.

**¿Cómo se manejan ofertas simultáneas?**
Control optimista: `actualizarVersionSiAbiertaYEnPlazo(idSubasta, version)` (IV.11). Si no afecta filas, se responde `CONFLICTO_CONCURRENTE` (IV.12b).

**¿Cómo se integran Java y .NET?**
Por AMQP, en `q.pagoSolicitud` (Java → .NET) y `q.pagoConfirmado` (.NET → Java), con JSON en camelCase. Por HTTP, `SrvRuteo → WSPagoYoPago` (VII.24).

**¿Cómo saben que el código coincide con el diagrama?**
`python3 -I tools/vpp/verificar_codigo.py` compara con `docs/modelo/modelo-canonico.json`, extraído del VPP, y CI lo ejecuta en cada PR. Además hay pruebas `ArquitecturaTest` (ArchUnit).
Si piden el modo estricto, ver la sección 8.1.

**¿Por qué el catálogo muestra `PUBLICADA` en una subasta ya cerrada?**
Porque el diagrama no le envía `SubastaCerrada` a Catálogo (observación 10). La regla la hace cumplir MSSubasta, que rechaza la oferta.

## 10. Decisiones y su justificación

Solo decisiones que constan en el repositorio. Si piden un motivo que no está aquí, se dice que no está documentado.

| Decisión | Motivo documentado | Fuente |
|---|---|---|
| El diagrama manda: clases, operaciones, puertos y colas con el mismo nombre | Fuente única de verdad, verificable en CI | `CLAUDE.md`, `verificar_codigo.py` |
| Outbox transaccional y consumidores idempotentes | Tolerar caídas de red y de OFAC sin perder eventos | ADR 0002 |
| Una base por servicio | Restricción del enunciado | ADR 0003 |
| Docker Compose por máquina, imágenes `linux/amd64` | Se construye en Mac arm64 y se ejecuta en VM; servicios fuera del IDE | ADR 0004 |
| OpenLDAP como AD | No hay un AD real; es el estereotipo del diagrama | ADR 0005 |
| `least_conn` y *competing consumers* | Escalabilidad y balanceo del servicio de compra | ADR 0006 |
| Monorepo | Equipo pequeño y un único `.zip` | ADR 0001 |
| Angular para SSO y ASP.NET Core MVC con Razor para YoPago | Razor cubre la presentación .NET del enunciado | `CLAUDE.md` |
| Mailpit y simulador HTTP para correo y SMS | Son sistemas externos sin componente en el diagrama | Observación 5 |
| Datos fuera del repositorio, cargados desde CSV | Regla del proyecto; el diagrama parte de subastas ya existentes | `CLAUDE.md` 3b, observación 9 |
| `consultarMisCompras` por las flechas de VI.4–VI.8 | El diagrama no trae esa consulta y no se agregan flechas | Observación 6 |
| Timer de MSCompra también dispara «despacho agotado» | El diagrama no dice qué dispara X.9 | Observación 12 |
| Sin cliente móvil | Decisión del usuario | Observación 15 |
| Subasta `LIBERADA` no vuelve; subasta nueva del mismo `id_bien` | Decisión del usuario; el diagrama no define «disponible» ni tiene pasos para volver a ofertar | Observación 16, #141 |

## 11. Plan B

Si las VM fallan el día de la sustentación:

**Entorno de un solo equipo (`deploy/dev`).** No cumple «se deben usar todas las máquinas», así que se dice abiertamente. Requiere `ssoweb`, que viene en #139.

```bash
cp deploy/dev/.env.example deploy/dev/.env   # y llenarlo
tools/scripts/generar-claves-token.sh deploy/secretos
tools/scripts/generar-certificado-tls.sh deploy/secretos
python3 -I tools/scripts/generar-usuario-mom.py <usuario> <clave> deploy/secretos/mom
docker compose -f deploy/dev/docker-compose.yml up -d --build
```

Diferencias con las VM:

- Usuarios con `--destino todo`.
- SSO en `http://localhost/` y YoPago en `http://localhost:5000/`.
- **Aquí sí** se ven los correos, en `http://localhost:8025`, y la consola de RabbitMQ, en `http://localhost:15672`.
- Si un puerto del host está ocupado, se cambia con `<SERVICIO>_PUERTO_HOST`.

**Video grabado.** **PENDIENTE (P21):** no existe. Se graba cuando el recorrido I a X funcione de punta a punta.

## 12. Lista de pendientes

### Con el profesor

| # | Pendiente | Responsable |
|---|---|---|
| P17 | Fechas de entrega y sustentación (en blanco en el enunciado), y duración de la sustentación | Usuario |
| P22 | «Todas las máquinas de todos los usuarios del grupo»: ¿tres VM cumplen, según el número de integrantes? | Usuario |

### Decisiones del equipo

| # | Pendiente | Referencia |
|---|---|---|
| P5 | Valores de `.env`: `MEMORIA_*` (se desconoce la RAM de las VM, observación 17), `TASA_IMPUESTO` (el enunciado no da la tasa), `IMAGE_REGISTRY` | #142 |
| P8 | Confirmar el túnel SSH a :8025 para los correos, y cómo alcanzar Mailpit, que el compose no publica en el host (5.3) | #142 |
| P9 | Puertos 443 y 8085 del balanceador, que el diagrama no dibuja (observación 18) | #142 |
| P23 | Numeración ya decidida (15 sin móvil, liberar bien 16, compose 17 y 18), pero quedan referencias por corregir: los `.env.example` de #142 citan «#15» por memoria (es la 17), y `SrvPago.cs` cita una «observación 15» sobre el contacto, que ahora es la del móvil | #142, `SrvPago.cs` |

### Por comprobar en las VM

| # | Pendiente | Referencia |
|---|---|---|
| P4 | Nada desplegado: Docker, imágenes, `up -d`, estado healthy y orden M3 → M2 → M1 | #68, #69, #70, #71 |
| P6 | Firewall, aislamiento real de la DMZ y segmentación de red. `tools/scripts/check-red.sh` sigue en curso, aún sin PR | #68 |
| P7 | `tools/carga` en las VM, incluidos `python3` y `docker compose ps -q` | #142 |
| P10 | Contenido de los CSV y tiempos de `fecha_inicio` y `fecha_cierre` | Ensayo |
| P16 | Comandos de apoyo de 5.3 y 7, incluido el log de `upstream=` | Ensayo |

### Por terminar o probar

| # | Pendiente | Referencia |
|---|---|---|
| P12 | CU VII a X en pantalla de punta a punta. `tools/scripts/e2e.sh` sigue en curso, aún sin PR. `tools/scripts/smoke-test.sh` (#143) no lo sustituye: es de solo lectura y no recorre el flujo | #65, #143 |
| P11 | Fusionar la decisión de liberar bien, y cargar en vivo una subasta nueva del mismo `id_bien` | #141 |
| P13 | Fusionar los PR de los que depende el guion, y con ellos el modo estricto. Al fusionar, unificar `deploy/README.md`, que #142 y #143 cambian por separado | #139, #140, #141, #142, #143 |
| P14 | Escenarios de falla sobre el sistema completo, y calibrar sus tiempos. `docs/escenarios-falla.md` no existe | #66 |
| P15 | Reporte con valor distinto (7.3) por la ruta completa hasta `LIBERADA` en las tres bases | #65, #66 |
| P18 | VPP con revisión cruzada e impresión en papel gigante | #73 |
| P19 | Generar el `.zip` de entrega. `Makefile` y `make zip` están en #143, sin fusionar | #143, #74 |
| P20 | Capturas y logs del aporte grupal en `docs/aporte-grupal.md` | #72 |
| P21 | Grabar el video del plan B | #75 |

# Reglas inquebrantables del proyecto SSO

Estas reglas mandan sobre cualquier otra preferencia, convención o "buena práctica". Si algo choca con ellas, gana la regla.

## 1. Fuente única de verdad

- **Única fuente:** el diagrama de despliegue **«Arquitectura Integrada JavaEE-.NET»**. Su versión impresa oficial es el pliego
  **`docs/enunciado/arquitectura-integrada-pliego-100x70.pdf`** (máquinas, nodos, componentes, clases, conexiones físicas y flechas de los
  pasos con su color). Los mismos datos, en forma legible por máquina, están en `ProyectoSSO-Integrada-JavaEE-NET.vpp`,
  `…-con-secuencias.vpp` y en el texto de cada paso de `casos-de-uso-arquitectura-integrada.pdf`. Los cuatro coinciden 1:1 (el extractor lo comprueba).
- **Prohibido usar** el diagrama EBC, el «Diagrama Fisico MS-SSO», el «Diagrama de componentes MS-SSO» y los diagramas DS: están desactualizados (no tienen .NET).
- El contenido de esa fuente está volcado en `docs/modelo/modelo-canonico.json`. **No se edita a mano**: se regenera con `python3 -I tools/vpp/extraer_modelo.py`.
- Si el diagrama no dice algo, **no se inventa**: se pregunta al usuario y se anota en `docs/modelo/observaciones.md`.

## 2. El código es idéntico al diagrama

| Elemento del diagrama | Cómo aparece en el código |
|---|---|
| Componente (`MSAcceso.jar`, `MSPago.dll`, `SSOWeb.zip`, `SSOMobile.apk`) | Artefacto con ese nombre exacto (`<finalName>`, `AssemblyName`, nombre del zip/apk) |
| Clase dentro del componente | Clase con el **mismo nombre exacto**, mayúsculas incluidas |
| Mensaje de un paso (`SrvLogin → IntegraLoginAD validarCredenciales(usuario, clave)`) | Método del destino con el **mismo nombre y los mismos parámetros en el mismo orden**; lo invoca la clase origen |
| Nodo `:puerto` | Puerto del servidor |
| Máquina e IP | `deploy/maquina-N` con esa IP |
| Estereotipo del nodo (`Tomcat embebido`, `Kestrel .NET 8`, `Nginx`, `PostgreSQL`, `RabbitMQ`, `OpenLDAP`, `SMTP`, `Android`) | Tecnología de ejecución |
| Conexión (`HTTP`, `HTTP/REST`, `HTTPS`, `AMQP`, `JDBC`, `LDAP`, `SMTP`) | Protocolo real usado entre esas dos clases |
| Cola `q.xxx` | Cola RabbitMQ con ese nombre exacto |
| Estados (`EN_VALIDACION`, `ESPERANDO_PAGO`, `PAGO_CONFIRMADO`, `TERMINADA`, `CANCELADA`, `EN_REVISION`, `APROBADO`, `RECHAZADO`, `ENPROCESO`) | Enum con esos literales exactos |

- Una clase **solo llama** a las clases a las que el diagrama le dibuja flecha. Nada de atajos (ej. `WSGateway` nunca llama directo a `WSSubasta`: pasa por `SrvSeguridadToken` → `SrvRuteo`).
- Cada método lleva Javadoc / comentario XML con los pasos que implementa: `/** Pasos IV.9, IV.11 */`.
- Paquete raíz del componente (`co.edu.javeriana.sso.<componente>` en Java, raíz del proyecto en .NET) = **exactamente** las clases del diagrama + la clase de arranque. Entidades, DTO, eventos, enums y configuración van en los subpaquetes `modelo` y `config`. Ninguna clase de negocio fuera del diagrama.

### Tecnología de las pantallas (decidida)

| Pantallas | Componente del diagrama | Tecnología | Clases |
|---|---|---|---|
| SSO: login, catálogo, subastas, ofertas, mis compras, publicar (admin) | `SSOWeb.zip` en Nginx :80 (Máquina 1) | **Angular**: compila a HTML/JS/CSS estáticos, se empaquetan en `SSOWeb.zip` y Nginx los sirve tal cual | `PantallaWebSubasta`, `ProxyWSSubastaWeb` |
| YoPago: consultar compra pendiente y pagar | `YoPagoFrontEnd.dll` en Kestrel .NET 8 :5000 (Máquina 1) | **ASP.NET Core MVC con Razor** (cubre la presentación .NET del enunciado) | `PantallaWebYoPago` (controlador MVC con `[Controller]`, vistas en `Views/PantallaWebYoPago/`), `ProxyWSYoPagoWeb` |
| SSO móvil | `SSOMobile.apk` (Android) | Android / Kotlin | `PantallaMobileSubasta`, `ProxyWSSubastaMobile` |

## 3. Requisitos del enunciado (`docs/enunciado/enunciado-proyecto-*.docx`)

- Todo en **Spring Boot**, salvo lo que el diagrama marca `.dll` / `Kestrel .NET 8` (.NET 8).
- **Prohibida la lógica de negocio en los front end** (`SSOWeb`, `SSOMobile`, `YoPagoFrontEnd`): solo presentación y llamadas `Proxy*`.
- Presentación y negocio en **máquinas separadas**; la web en el perímetro aislado (Máquina 1, DMZ).
- **Bases de datos distintas** por sistema; ningún servicio se conecta a la base de otro.
- Diseño tolerante a caídas de red y de OFAC (`APROBADO` / `RECHAZADO` / `ENPROCESO`).
- Escalabilidad y balanceo del servicio de compra (dos réplicas de `MSCompra` detrás de Nginx `least_conn`).
- Servicios desplegados **fuera del IDE**, en Docker, usando las tres máquinas con sus IP.
- Entregable de código: `.zip` con todos los `pom.xml`.

## 4. Verificación obligatoria

Antes de cada commit:

```bash
python3 -I tools/vpp/verificar_codigo.py
```

Debe terminar en ✓. CI lo ejecuta en cada PR y bloquea el merge si falla. Para la entrega: `--estricto`.

## 5. Git

- Conventional Commits, rama `tipo/N-descripcion`, PR con `Closes #N` (ver `CONTRIBUTING.md`).
- **Sin** `Co-Authored-By` ni atribución a Claude en commits ni PRs.

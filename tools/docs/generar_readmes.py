"""Genera el README de cada componente con la misma plantilla: responsabilidad, despliegue, endpoints, eventos y variables.

Solo la responsabilidad se escribe a mano (tabla RESPONSABILIDAD). Lo demás se lee del código, del modelo
canónico y de deploy/dev/docker-compose.yml. Falla si las variables del código y del compose no coinciden.

Uso: python3 -I tools/docs/generar_readmes.py [--comprobar]
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fuentes as f  # noqa: E402
import generar_contratos as contratos  # noqa: E402

RESPONSABILIDAD = {
    "apigateway": "Único punto de entrada de la DMZ. `SrvSeguridadToken` valida el token RS256 emitido por MSAcceso (y la credencial de YoPago en el reporte de pago) y `SrvRuteo` envía la petición al componente interno. Sin lógica de negocio: devuelve la respuesta del componente sin modificarla.",
    "msacceso": "Autentica al cliente: valida usuario y clave contra el AD por LDAP, lee sus datos en DB ACCESO (que no guarda claves) y emite el token RS256.",
    "mscatalogo": "Consulta paginada del catálogo y publicación de subastas (de PENDIENTE a PUBLICADA). Anuncia cada subasta publicada en `q.nuevaSubasta` por outbox y libera la subasta cuando una compra se compensa.",
    "mssubasta": "Registra ofertas con control optimista de versión, abre y cierra subastas con sus timers, y publica los cambios por outbox. Libera la subasta cuando una compra se compensa.",
    "mssuscripcion": "Administra las suscripciones de los clientes (`NUEVAS_SUBASTAS`, `CAMBIOS_OFERTA`) y convierte los eventos de subasta en notificaciones para los suscriptores.",
    "mscompra": "Orquesta la saga de compra: valida al comprador ante la OFAC, solicita el pago, solicita el despacho y compensa si algo falla (estados en `EstadoCompra`). Corre en dos réplicas detrás de Nginx (`least_conn`) que comparten DB COMPRA.",
    "msvalidacion": "Consulta a la OFAC por la cédula del comprador y guarda el resultado. Un resultado definitivo (`APROBADO`, `RECHAZADO`) no se vuelve a consultar; `ENPROCESO` sí.",
    "msdespacho": "Coordina el despacho: solicita la salida de inventario a SIN y el envío a SEN, y confirma el despacho a MSCompra.",
    "msnotificacion": "Entrega las notificaciones de `q.notificacion` por correo (SMTP) y, si el mensaje lo pide, por SMS. Reintenta con espera creciente; al agotar los intentos el mensaje pasa a `q.notificacion.dlq`.",
    "ofacbackend": "Sistema externo OFAC. Responde el estado de una cédula según DB OFAC: sin reporte `APROBADO`, `REPORTADO` da `RECHAZADO` y `EN_ESTUDIO` da `ENPROCESO`.",
    "sinbackend": "Sistema de Inventarios. Registra la salida del bien de cada compra, una sola vez aunque el mensaje se repita, y la confirma por outbox.",
    "senbackend": "Sistema de Envíos. Programa el envío de cada compra, una sola vez aunque el mensaje se repita, y confirma la fecha programada por outbox.",
    "mspago": "Puente .NET del pago. Recibe la solicitud de MSCompra, calcula el valor con impuestos, registra la compra pendiente en YoPago y avisa al cliente con la URL de pago. Concilia el reporte de YoPago y publica el resultado en `q.pagoConfirmado`.",
    "yopagobackend": "Sistema externo YoPago. Guarda las compras pendientes, valida las credenciales del cliente en YoPago, registra el pago y lo reporta a SSO por HTTPS, reintentando en segundo plano si SSO no responde.",
    "yopagofrontend": "Sitio de YoPago (ASP.NET Core MVC con Razor): ingreso, compras pendientes y pago. Solo presentación: todo lo resuelve `WSCompraPendiente` a través de `ProxyWSYoPagoWeb`.",
    "ssoweb": "Pantallas de SSO (Angular): login, catálogo, suscripciones, ofertas, mis compras y publicación. Solo presentación: toda decisión la toma el backend a través de `ProxyWSSubastaWeb`.",
}
DESCRIPCION_VARIABLE = {
    "DB_URL": "URL JDBC de su base de datos", "DB_HOST": "Host de su base de datos", "DB_NOMBRE": "Nombre de su base de datos",
    "DB_USUARIO": "Usuario de su base de datos", "DB_CLAVE": "Clave de su base de datos",
    "MOM_HOST": "Host de RabbitMQ", "MOM_USUARIO": "Usuario de RabbitMQ", "MOM_CLAVE": "Clave de RabbitMQ",
    "AD_URL": "URL LDAP del AD", "TOKEN_CLAVE_PRIVADA": "Ruta del PEM con la clave privada del token",
    "TOKEN_CLAVE_PUBLICA": "Ruta del PEM con la clave pública del token",
    "CREDENCIAL_YOPAGO": "Credencial compartida del reporte de pago", "CERTIFICADO_SSO": "Ruta del certificado TLS del balanceador",
    "RUTA_MSACCESO": "URL de WSAcceso", "RUTA_MSCATALOGO": "URL de WSCatalogo", "RUTA_MSSUBASTA": "URL de WSSubasta",
    "RUTA_MSSUSCRIPCION": "URL de WSSuscripcion", "RUTA_BALANCEADOR_COMPRA": "URL de Nginx hacia el upstream msCompra",
    "RUTA_MSPAGO": "URL de WSPagoYoPago", "RUTA_MSVALIDACION": "URL de WSValidacion", "RUTA_OFAC": "URL de WSOfac",
    "RUTA_SMS": "URL de SMS SERVER", "RUTA_YOPAGO": "URL de WSCompraPendiente", "RUTA_SSO": "URL HTTPS de Nginx hacia SSO",
    "MAIL_HOST": "Host SMTP", "MAIL_REMITENTE": "Remitente de los correos", "TASA_IMPUESTO": "Tasa de impuestos sobre el valor ofertado",
    "URL_PAGO": "URL de YoPagoWeb que se envía al cliente",
}


def tabla(encabezados, filas):
    lineas = ["| " + " | ".join(encabezados) + " |", "|" + "---|" * len(encabezados)]
    for fila in filas:
        lineas.append("| " + " | ".join(str(c).replace("|", "\\|") for c in fila) + " |")
    return "\n".join(lineas)


def despliegue(artefacto):
    componente = f.componente_modelo(artefacto)
    nodos = tabla(["Nodo", "Puerto", "Máquina", "Tecnología"],
                  [(n["nodo"], n["puerto"], n["maquina"], ", ".join(n["tecnologia"])) for n in componente["nodos"]])
    clases = ", ".join(f"`{c}`" for c in componente["clases"])
    return f"## Despliegue\n\nArtefacto `{artefacto}`.\n\n{nodos}\n\nClases del diagrama: {clases}."


def conexiones(artefacto):
    clases = set(f.componente_modelo(artefacto)["clases"])
    vistas = sorted({(c["protocolo"], c["de"], c["a"]) for c in f.MODELO["conexiones"] if c["de"] in clases or c["a"] in clases})
    return "## Conexiones del diagrama\n\n" + tabla(["Protocolo", "De", "A"], [(p, f"`{d}`", f"`{a}`") for p, d, a in vistas])


def forma(modulo, tipo):
    registros = f.tipos(modulo)[0]
    if tipo in registros:
        return f"`{tipo}` {{{', '.join(c['nombre'] for c in registros[tipo])}}}"
    return f"`{tipo}`"


def entrada(modulo, op):
    partes = []
    rutas = re.findall(r"\{(\w+)\}", op["ruta"])
    for p in op["parametros"]:
        if p["nombre"] in rutas or "RequestHeader" in p["anotaciones"]:
            continue
        if "RequestParam" in p["anotaciones"]:
            partes.append(f"query `{p['nombre']}`")
        elif p["tipo"] == "Filtros":
            partes.append("query " + ", ".join(f"`{c['nombre']}`" for c in f.tipos(modulo)[0]["Filtros"]))
        elif p["tipo"] in ("string", "String"):
            partes.append(f"`{p['nombre']}`")
        else:
            partes.append("cuerpo " + forma(modulo, p["tipo"]))
    return ", ".join(partes) or "—"


def salida(modulo, op):
    if modulo == "apigateway":
        destino, nombre = contratos.RUTEO[op["operacion"]]
        final = next(o for o in f.operaciones(destino) if o["operacion"] == nombre)
        return f"{final['estado']} `{final['retorno']}` de {f.COMPONENTES[destino][0]}"
    return f"{op['estado']} `{op['retorno']}`"


def endpoints(modulo):
    ops = f.operaciones(modulo)
    salud = "/actuator/health" if f.lenguaje(modulo) == "java" else "/health"
    if modulo == "yopagofrontend":
        filas = [(o["metodo"].upper(), f"`{o['ruta']}`", f"`{o['operacion']}`", entrada(modulo, o), o["descripcion"] or "—") for o in ops]
        return ("## Endpoints\n\nDevuelven vistas Razor (`Views/PantallaWebYoPago/`), no JSON, y los POST exigen el token antiforgery del formulario.\n\n"
                + tabla(["Método", "Ruta", "Operación", "Entrada", "Pasos"], filas)
                + f"\n\nSalud: `GET {salud}`.")
    if not ops:
        return f"## Endpoints\n\nNo expone API de negocio: trabaja por colas. Salud: `GET {salud}`."
    filas = [(o["metodo"].upper(), f"`{o['ruta']}`", f"`{o['operacion']}`", entrada(modulo, o), salida(modulo, o), o["descripcion"] or "—") for o in ops]
    return (f"## Endpoints\n\nContrato completo, con errores: [`docs/contratos/openapi/{modulo}.yaml`](/docs/contratos/openapi/{modulo}.yaml).\n\n"
            + tabla(["Método", "Ruta", "Operación", "Entrada", "Éxito", "Pasos"], filas)
            + f"\n\nSalud: `GET {salud}`.")


def eventos(modulo):
    consume = f.consumidas(modulo)
    publica = f.producidas(modulo)
    if not consume and not publica:
        return "## Eventos\n\nNo publica ni consume colas."
    partes = ["## Eventos\n\nEsquemas: [`docs/contratos/asyncapi.yaml`](/docs/contratos/asyncapi.yaml)."]
    if consume:
        partes.append("Consume:\n\n" + tabla(["Cola", "Clase"], [(f"`{c}`", f"`{k}`") for c, k in sorted(consume.items())]))
    if publica:
        partes.append("Publica (outbox):\n\n" + tabla(["Exchange", "Evento", "Clase"],
                                                      [(f"`{c}`", f"`{contratos.MENSAJE.get((modulo, r), r)}`", f"`{w}`") for c, r, w in publica]))
    return "\n\n".join(partes)


def seccion_variables(modulo):
    codigo = f.variables(modulo)
    compose = f.entorno_compose(f.COMPONENTES[modulo][3])
    if set(codigo) != set(compose):
        raise SystemExit(f"✗ {modulo}: variables del código {sorted(codigo)} ≠ deploy/dev {sorted(compose)}")
    filas = []
    for variable, uso in codigo.items():
        if variable not in DESCRIPCION_VARIABLE:
            raise SystemExit(f"✗ {modulo}: falta describir la variable {variable}")
        filas.append((f"`{variable}`", DESCRIPCION_VARIABLE[variable], f"`{uso}`", f"`{compose[variable]}`"))
    return ("## Variables de entorno\n\nTodas son obligatorias: sin valor por defecto, el servicio no arranca. "
            "Llegan desde `deploy/maquina-N/.env`.\n\n"
            + tabla(["Variable", "Qué es", "Dónde se usa", "En `deploy/dev`"], filas))


def construir(modulo):
    if f.lenguaje(modulo) == "java":
        prueba = f"cd services && ./mvnw -B -pl {modulo} -am verify"
    else:
        prueba = "make test-dotnet"
    return ("## Construir y probar\n\n```bash\n" + prueba + "\n"
            f"make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s={modulo}\n```")


AVISO = "> Generado por `tools/docs/generar_readmes.py` desde el código y el modelo canónico. Para cambiarlo: `make readmes`."


def readme_componente(modulo):
    artefacto = f.COMPONENTES[modulo][0]
    secciones = [f"# {artefacto.rsplit('.', 1)[0]}", AVISO, "## Responsabilidad\n\n" + RESPONSABILIDAD[modulo], despliegue(artefacto),
                 endpoints(modulo), eventos(modulo), seccion_variables(modulo), conexiones(artefacto), construir(modulo)]
    return "\n\n".join(secciones) + "\n"


def readme_ssoweb():
    proxy = (f.RAIZ / "frontend/sso-web/src/app/proxy-ws-subasta-web.ts").read_text(encoding="utf-8")
    filas = []
    for m in re.finditer(r"^  (\w+)\([^)]*\): Promise<[^\n]*\{(.*?)^  \}", proxy, flags=re.S | re.M):
        llamada = re.search(r"this\.http\.(\w+)<[^(]*>\(\s*[`']([^`']+)[`']", m.group(2))
        if llamada:
            ruta = re.sub(r"\$\{encodeURIComponent\((\w+)\)\}", r"{\1}", llamada.group(2))
            filas.append((f"`{m.group(1)}`", llamada.group(1).upper(), f"`{ruta}`"))
    secciones = [
        "# SSOWeb", AVISO, "## Responsabilidad\n\n" + RESPONSABILIDAD["ssoweb"], despliegue("SSOWeb.zip"),
        "## Endpoints\n\nNo expone API: Nginx sirve archivos estáticos (`nginx.conf`). `ProxyWSSubastaWeb` llama al mismo Nginx con rutas relativas; "
        "contrato en [`docs/contratos/openapi/apigateway.yaml`](/docs/contratos/openapi/apigateway.yaml).\n\n"
        + tabla(["Operación", "Método", "Ruta"], filas),
        "## Eventos\n\nNo publica ni consume colas.",
        "## Variables de entorno\n\nNinguna: las rutas son relativas al Nginx que sirve la página.",
        conexiones("SSOWeb.zip"),
        "## Construir y probar\n\n```bash\nnpm ci\nnpm run lint\nnpm test -- --watch=false\nnpm run build\n"
        "make images-local IMAGE_REGISTRY=<registro> IMAGE_TAG=<etiqueta> s=ssoweb   # desde la raíz; empaqueta SSOWeb.zip\n```",
    ]
    return "\n\n".join(secciones) + "\n"


def main():
    archivos = {f.carpeta(m) / "README.md": readme_componente(m) for m in f.COMPONENTES}
    archivos[f.RAIZ / "frontend/sso-web/README.md"] = readme_ssoweb()
    f.escribir_o_comprobar(archivos, "--comprobar" in sys.argv)


if __name__ == "__main__":
    main()

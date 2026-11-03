"""Genera docs/contratos: OpenAPI de cada componente con API HTTP, AsyncAPI de las colas y ejemplos JSON.

Lee rutas, cuerpos y colas del código (ver fuentes.py) y falla si el código, el diagrama y
deploy/mom/definiciones/colas.json no coinciden.

Uso: python3 -I tools/docs/generar_contratos.py [--comprobar]
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fuentes as f  # noqa: E402

SALIDA = f.RAIZ / "docs/contratos"
PRIMITIVOS = {
    "String": {"type": "string"}, "string": {"type": "string"},
    "UUID": {"type": "string", "format": "uuid"}, "Guid": {"type": "string", "format": "uuid"},
    "BigDecimal": {"type": "number"}, "decimal": {"type": "number"},
    "Instant": {"type": "string", "format": "date-time"},
    "LocalDate": {"type": "string", "format": "date"},
    "int": {"type": "integer"}, "Integer": {"type": "integer"}, "long": {"type": "integer"},
    "boolean": {"type": "boolean"}, "bool": {"type": "boolean"},
}

# Componentes con API HTTP de datos (YoPagoFrontEnd devuelve vistas Razor: va en su README).
CON_API = ["apigateway", "msacceso", "mscatalogo", "mssubasta", "mssuscripcion", "mscompra", "msvalidacion",
           "ofacbackend", "mspago", "yopagobackend"]

# Operación → excepciones que puede lanzar. El código HTTP de cada una se lee de su manejador.
ERRORES = {
    ("msacceso", "autenticar"): ["CredencialesInvalidasException", "ClienteNoRegistradoException", "AdNoDisponibleException"],
    ("mscatalogo", "publicarNuevaSubasta"): ["SubastaNoEncontradaException", "SubastaNoPublicableException"],
    ("mssuscripcion", "cancelarSuscripcion"): ["SuscripcionNoEncontradaException"],
    ("mscompra", "iniciarCompra"): ["CompraNoEncontradaException", "CompraAjenaException", "CompraNoIniciableException"],
    ("msvalidacion", "validar"): ["OfacNoDisponibleException"],
    ("mspago", "reportarPago"): ["PagoNoEncontradoException"],
    ("yopagobackend", "consultarCompraPendiente"): ["CredencialesInvalidasException"],
    ("yopagobackend", "pagar"): ["CredencialesInvalidasException", "CompraNoEncontradaException", "CompraYaPagadaException"],
}
# WSSubasta.rechazo: estos no pasan por un ManejoErrores. SubastaNoEncontradaException no tiene manejador.
ERRORES_DIRECTOS = {
    ("mssubasta", "registrarOferta"): {
        "409": "CONFLICTO_CONCURRENTE (propiedad motivo)",
        "422": "SUBASTA_NO_ABIERTA, FUERA_DE_PLAZO, MENOR_AL_MINIMO o NO_SUPERA_OFERTA_MAYOR (propiedad motivo)",
    },
}
# Método de WSGateway → destino al que SrvRuteo lo envía. La respuesta del destino pasa sin cambios.
RUTEO = {
    "autenticar": ("msacceso", "autenticar"),
    "consultarCatalogo": ("mscatalogo", "consultarCatalogo"),
    "suscribirse": ("mssuscripcion", "suscribirse"),
    "cancelarSuscripcion": ("mssuscripcion", "cancelarSuscripcion"),
    "publicarNuevaSubasta": ("mscatalogo", "publicarNuevaSubasta"),
    "registrarOferta": ("mssubasta", "registrarOferta"),
    "consultarMisCompras": ("mscompra", "consultarCompras"),
    "iniciarCompra": ("mscompra", "iniciarCompra"),
    "reportarPago": ("mspago", "reportarPago"),
}
# Excepciones de SrvSeguridadToken por operación de WSGateway.
ERRORES_GATEWAY = {
    "autenticar": ["ServicioNoDisponibleException"],
    "publicarNuevaSubasta": ["TokenInvalidoException", "AccesoDenegadoException", "ServicioNoDisponibleException"],
    "reportarPago": ["AccesoDenegadoException", "ServicioNoDisponibleException"],
}
SIN_TOKEN = {"autenticar", "reportarPago"}
NOTAS = {
    ("apigateway", "reportarPago"): "Solo por HTTPS: Nginx responde 403 a esta ruta en el puerto 80. Se autentica con el campo `credencial`, no con token.",
    ("mscompra", "iniciarCompra"): "Se llega por Nginx (upstream msCompra, least_conn) a cualquiera de las dos réplicas.",
    ("mssubasta", "registrarOferta"): "Una subasta inexistente lanza SubastaNoEncontradaException, que hoy no tiene manejador: responde 500.",
}

# Eventos: (módulo, record) → nombre del mensaje, cuando el mismo record existe en varios productores.
MENSAJE = {("mssuscripcion", "Notificacion"): "NotificacionSuscripcion", ("mscompra", "Notificacion"): "NotificacionCompra"}
# Campos que el contrato declara nulos (docs/modelo/eventos.md) y que el record de Java no marca.
NULABLES = {("mssubasta", "CambioOfertaMayor", "anterior"), ("mssubasta", "SubastaCerrada", "ganador")}
# Valores fijos de campos que son texto en el productor, leídos del enum del consumidor.
ENUM_CAMPO = {("mspago", "PagoConfirmado", "resultado"): ("mscompra", "ResultadoPago")}
DESCRIPCION_CAMPO = {
    ("mssubasta", "SubastaCerrada", "resultado"): "ADJUDICADA o DESIERTA.",
    ("mssubasta", "CambioOfertaMayor", "anterior"): "Null si es la primera oferta.",
    ("mssubasta", "SubastaCerrada", "ganador"): "Null si la subasta quedó desierta.",
}
PROBLEMA = {
    "type": "object",
    "description": "Problem Details (RFC 9457).",
    "properties": {"type": {"type": "string"}, "title": {"type": "string"}, "status": {"type": "integer"},
                   "detail": {"type": "string"}, "instance": {"type": "string"}, "motivo": {
                       "type": "string", "description": "Solo en el rechazo de ofertas de MSSubasta."}},
}
ERROR_SPRING = {
    "type": "object",
    "description": "Cuerpo por defecto de Spring Boot para una petición inválida.",
    "properties": {"timestamp": {"type": "string", "format": "date-time"}, "status": {"type": "integer"},
                   "error": {"type": "string"}, "path": {"type": "string"}},
}


def esquema_tipo(modulo, tipo, usados):
    if tipo in PRIMITIVOS:
        return dict(PRIMITIVOS[tipo])
    lista = re.fullmatch(r"(?:List|IReadOnlyList)<(\w+)>", tipo)
    if lista:
        return {"type": "array", "items": esquema_tipo(modulo, lista.group(1), usados)}
    return {"$ref": f"#/components/schemas/{registrar(modulo, tipo, usados)}"}


def registrar(modulo, nombre, usados, alias=None):
    """Agrega el esquema y devuelve su nombre. Si otro módulo define distinto ese nombre (Cliente de
    MSCompra lleva direccion), este queda como Nombre_modulo."""
    registros, enums = f.tipos(modulo)
    if nombre in enums:
        nuevo = {"type": "string", "enum": enums[nombre]}
    elif nombre in registros:
        propiedades, requeridos = {}, []
        for campo in registros[nombre]:
            propiedades[campo["nombre"]] = esquema_campo(modulo, nombre, campo, usados)
            if campo["requerido"]:
                requeridos.append(campo["nombre"])
        nuevo = {"type": "object"}
        if requeridos:
            nuevo["required"] = requeridos
        nuevo["properties"] = propiedades
    else:
        raise SystemExit(f"✗ {modulo}: tipo desconocido {nombre}")
    clave = alias or nombre
    if clave in usados and usados[clave] != nuevo:
        clave = f"{clave}_{modulo}"
    usados[clave] = nuevo
    return clave


def esquema_campo(modulo, registro, campo, usados):
    base = esquema_tipo(modulo, campo["tipo"], usados)
    clave = (modulo, registro, campo["nombre"])
    if clave in ENUM_CAMPO:
        origen, enum = ENUM_CAMPO[clave]
        base["enum"] = f.tipos(origen)[1][enum]
    if "Positive" in campo["anotaciones"]:
        base["exclusiveMinimum"] = 0
    patron = re.search(r'regexp = "(.*)"', campo["anotaciones"].get("Pattern", ""))
    if patron:
        base["pattern"] = patron.group(1).replace("\\\\", "\\")
    if campo["nulable"] or clave in NULABLES:
        if "$ref" in base:
            base = {"oneOf": [base, {"type": "null"}]}
        else:
            base["type"] = [base["type"], "null"]
    if clave in DESCRIPCION_CAMPO:
        base["description"] = DESCRIPCION_CAMPO[clave]
    return base


def respuesta_json(descripcion, esquema, tipo="application/json"):
    return {"description": descripcion, "content": {tipo: {"schema": esquema}}}


def _operacion(modulo, nombre):
    return next(o for o in f.operaciones(modulo) if o["operacion"] == nombre)


def _errores_de(modulo, nombre, tablas):
    salida = {}
    for excepcion in tablas.get((modulo, nombre), []):
        codigo = f.errores(modulo)[excepcion]
        salida.setdefault(str(codigo), []).append(excepcion)
    return salida


def respuestas(modulo, op, usados):
    salida = {}
    modulo_destino, op_destino = modulo, op
    errores = {}
    if modulo == "apigateway":
        modulo_destino, nombre = RUTEO[op["operacion"]]
        op_destino = _operacion(modulo_destino, nombre)
        propias = ERRORES_GATEWAY.get(op["operacion"], ["TokenInvalidoException", "ServicioNoDisponibleException"])
        for excepcion in propias:
            errores.setdefault(str(f.errores("apigateway")[excepcion]), []).append(f"WSGateway: {excepcion}")
        for codigo, nombres in _errores_de(modulo_destino, op_destino["operacion"], ERRORES).items():
            errores.setdefault(codigo, []).extend(f"{modulo_destino}: {n}" for n in nombres)
        for codigo, texto in ERRORES_DIRECTOS.get((modulo_destino, op_destino["operacion"]), {}).items():
            errores.setdefault(codigo, []).append(f"{modulo_destino}: {texto}")
    else:
        for codigo, nombres in _errores_de(modulo, op["operacion"], ERRORES).items():
            errores.setdefault(codigo, []).extend(nombres)
        for codigo, texto in ERRORES_DIRECTOS.get((modulo, op["operacion"]), {}).items():
            errores.setdefault(codigo, []).append(texto)
    salida[str(op_destino["estado"])] = respuesta_json(
        "Respuesta del componente.", esquema_tipo(modulo_destino, op_destino["retorno"], usados))
    tiene_entrada = any(p["tipo"] not in ("String", "string") or "Pattern" in p["anotaciones"] or "RequestParam" in p["anotaciones"]
                        for p in op["parametros"] if p["nombre"] != "token")
    if tiene_entrada and op["parametros"] and op["operacion"] not in ("consultarCatalogo",):
        if f.lenguaje(modulo) == "java":
            salida["400"] = respuesta_json("Petición inválida.", {"$ref": "#/components/schemas/ErrorSpring"})
            usados["ErrorSpring"] = ERROR_SPRING
        else:
            salida["400"] = respuesta_json("Petición inválida.", {"$ref": "#/components/schemas/Problema"}, "application/problem+json")
            usados["Problema"] = PROBLEMA
    for codigo in sorted(errores):
        salida[codigo] = respuesta_json("; ".join(errores[codigo]), {"$ref": "#/components/schemas/Problema"}, "application/problem+json")
        usados["Problema"] = PROBLEMA
    return salida


def parametros_y_cuerpo(modulo, op, usados):
    parametros, cuerpo = [], None
    rutas = re.findall(r"\{(\w+)\}", op["ruta"])
    registros = f.tipos(modulo)[0]
    for p in op["parametros"]:
        if "RequestHeader" in p["anotaciones"]:
            continue
        if p["nombre"] in rutas:
            esquema = esquema_campo(modulo, "", p, usados)
            parametros.append({"name": p["nombre"], "in": "path", "required": True, "schema": esquema})
        elif "RequestParam" in p["anotaciones"]:
            parametros.append({"name": p["nombre"], "in": "query", "required": True, "schema": esquema_tipo(modulo, p["tipo"], usados)})
        elif p["tipo"] == "Filtros":
            for campo in registros["Filtros"]:
                parametros.append({"name": campo["nombre"], "in": "query", "required": False, "schema": esquema_tipo(modulo, campo["tipo"], usados)})
        else:
            cuerpo = {"required": True, "content": {"application/json": {"schema": esquema_tipo(modulo, p["tipo"], usados)}}}
    return parametros, cuerpo


def openapi(modulo):
    artefacto = f.COMPONENTES[modulo][0]
    nodos = f.componente_modelo(artefacto)["nodos"]
    usados, rutas = {}, {}
    seguro = False
    for op in f.operaciones(modulo):
        parametros, cuerpo = parametros_y_cuerpo(modulo, op, usados)
        descripcion = op["descripcion"]
        nota = NOTAS.get((modulo, op["operacion"]))
        operacion = {"operationId": op["operacion"], "summary": f"{op['clase']}.{op['operacion']}"}
        if descripcion or nota:
            operacion["description"] = " ".join(x for x in (descripcion, nota) if x)
        if modulo == "apigateway" and op["operacion"] not in SIN_TOKEN:
            operacion["security"] = [{"token": []}]
            seguro = True
        else:
            operacion["security"] = []  # Los componentes internos confían en la red; el token lo valida WSGateway
        if parametros:
            operacion["parameters"] = parametros
        if cuerpo:
            operacion["requestBody"] = cuerpo
        operacion["responses"] = respuestas(modulo, op, usados)
        rutas.setdefault(op["ruta"], {})[op["metodo"]] = operacion
    salud = "/actuator/health" if f.lenguaje(modulo) == "java" else "/health"
    rutas[salud] = {"get": {
        "operationId": "salud", "summary": "Estado del componente", "security": [],
        "responses": {"200": respuesta_json("En servicio.", {"type": "object", "properties": {"status": {"type": "string"}}})},
    }}
    esquemas = {k: usados[k] for k in sorted(usados)}
    componentes = {"schemas": esquemas}
    if seguro:
        componentes["securitySchemes"] = {"token": {"type": "http", "scheme": "bearer", "bearerFormat": "JWT",
                                                     "description": "Token RS256 emitido por MSAcceso."}}
    return {
        "openapi": "3.1.0",
        "info": {"title": artefacto, "version": "1.0.0", "license": {"name": "Proyecto académico", "identifier": "UNLICENSED"},
                 "description": f"Generado desde el código con tools/docs/generar_contratos.py. Nodo del diagrama: {', '.join(n['nodo'] for n in nodos)}."},
        "servers": [{"url": f"http://{n['ip']}:{n['puerto']}", "description": f"{n['nodo']} · {n['maquina']}"} for n in nodos],
        "paths": rutas,
        "components": componentes,
    }


def eventos():
    """Mensajes por cola del diagrama, contrastados con el diagrama y con las definiciones de RabbitMQ."""
    productores, consumidores = {}, {}
    for modulo in f.COMPONENTES:
        for cola, registro, worker in f.producidas(modulo):
            productores.setdefault(cola, []).append((modulo, registro, worker))
        for cola, listener in f.consumidas(modulo).items():
            diagrama = ".".join(cola.split(".")[:2])
            consumidores.setdefault(diagrama, []).append((modulo, listener, cola))
    dibujados_p, dibujados_c = f.amqp_del_diagrama()
    fallas = []
    for cola in sorted(set(dibujados_p) | set(dibujados_c) | set(productores) | set(consumidores)):
        if {p[2] for p in productores.get(cola, [])} != dibujados_p.get(cola, set()):
            fallas.append(f"{cola}: productores del código {productores.get(cola)} ≠ diagrama {dibujados_p.get(cola)}")
        if {c[1] for c in consumidores.get(cola, [])} != dibujados_c.get(cola, set()):
            fallas.append(f"{cola}: consumidores del código {consumidores.get(cola)} ≠ diagrama {dibujados_c.get(cola)}")
    definiciones = json.loads((f.RAIZ / "deploy/mom/definiciones/colas.json").read_text(encoding="utf-8"))
    intercambios = {e["name"]: e for e in definiciones["exchanges"] if e["name"].startswith("q.")}
    if set(intercambios) != set(productores):
        fallas.append(f"colas.json {sorted(intercambios)} ≠ código {sorted(productores)}")
    colas = {q["name"]: q for q in definiciones["queues"]}
    for lista in consumidores.values():
        fallas += [f"{c}: no está en colas.json" for _, _, c in lista if c not in colas]
    # Cada consumidor debe leer campos que el productor envía
    for cola, lista in consumidores.items():
        enviados = set()
        for modulo, registro, _ in productores[cola]:
            enviados |= {c["nombre"] for c in f.tipos(modulo)[0][registro]}
        for modulo, listener, _ in lista:
            for nombre in re.findall(r"\b(\w+)\s+\w+[,)]", next(p for p in f._fuentes(modulo) if p.stem == listener).read_text(encoding="utf-8")):
                campos = f.tipos(modulo)[0].get(nombre)
                if campos and any(c["nombre"] not in enviados for c in campos) and "idEvento" in {c["nombre"] for c in campos}:
                    fallas.append(f"{cola}: {modulo}.{nombre} espera campos que nadie envía: "
                                  f"{[c['nombre'] for c in campos if c['nombre'] not in enviados]}")
    if fallas:
        raise SystemExit("✗ Código, diagrama y colas.json no coinciden:\n  " + "\n  ".join(fallas))
    return productores, consumidores, intercambios, colas


def _tipos_amqp(modulo, registro):
    """Valores de la propiedad AMQP type: los literales Notificacion* del servicio o el nombre del record."""
    tipos = set()
    if registro.startswith("Notificacion"):
        for ruta in f.carpeta(modulo).rglob("Srv*"):
            tipos |= set(re.findall(r'"(Notificacion\w*)"', ruta.read_text(encoding="utf-8")))
    return sorted(tipos) or [registro]


def asyncapi():
    productores, consumidores, intercambios, colas = eventos()
    esquemas, mensajes, canales, operaciones = {}, {}, {}, {}
    for cola in sorted(productores):
        clave = cola.removeprefix("q.")
        refs = {}
        for modulo, registro, _ in productores[cola]:
            nombre = MENSAJE.get((modulo, registro), registro)
            registrar(modulo, registro, esquemas, alias=nombre)
            tipos = _tipos_amqp(modulo, registro)
            mensajes[nombre] = {
                "name": nombre, "contentType": "application/json",
                "description": f"Lo publica {f.COMPONENTES[modulo][0]}. Propiedades AMQP: message_id = idEvento, type = {' | '.join(tipos)}, persistente.",
                "payload": {"$ref": f"#/components/schemas/{nombre}"},
            }
            refs[nombre] = {"$ref": f"#/components/messages/{nombre}"}
        reales = sorted({c for _, _, c in consumidores[cola]})
        limite = colas[reales[0]]["arguments"]["x-delivery-limit"]
        canales[clave] = {
            "address": cola,
            "description": f"Exchange fanout {cola}, ligado a: {', '.join(reales)}. Tras {limite} entregas fallidas el mensaje pasa a <cola>.dlq.",
            "messages": refs,
            "bindings": {"amqp": {"is": "routingKey", "bindingVersion": "0.3.0", "exchange": {
                "name": cola, "type": intercambios[cola]["type"], "durable": True, "autoDelete": False, "vhost": "/"}}},
        }
        for worker in sorted({p[2] for p in productores[cola]}):
            nombres = [MENSAJE.get((m, r), r) for m, r, w in productores[cola] if w == worker]
            operaciones[f"{worker}_{clave}"] = {
                "action": "send", "channel": {"$ref": f"#/channels/{clave}"}, "summary": f"{worker} publica en {cola}",
                "messages": [{"$ref": f"#/channels/{clave}/messages/{n}"} for n in nombres],
            }
        for modulo, listener, real in sorted(consumidores[cola]):
            operaciones[f"{listener}_{real.removeprefix('q.').replace('.', '_')}"] = {
                "action": "receive", "channel": {"$ref": f"#/channels/{clave}"},
                "summary": f"{listener} ({f.COMPONENTES[modulo][0]}) consume la cola {real}",
            }
    mom = next(i for i in f.MODELO["infraestructura"] if "RabbitMQ" in i["tecnologia"])
    return {
        "asyncapi": "3.0.0",
        "info": {"title": "Eventos SSO", "version": "1.0.0",
                 "description": "Generado desde el código con tools/docs/generar_contratos.py. Explicación de cada evento y sus pasos: docs/modelo/eventos.md."},
        "defaultContentType": "application/json",
        "servers": {"mom": {"host": f"{mom['ip']}:{mom['puerto']}", "protocol": "amqp", "description": mom["nodo"]}},
        "channels": canales,
        "operations": operaciones,
        "components": {"messages": mensajes, "schemas": {k: esquemas[k] for k in sorted(esquemas)}},
    }, esquemas


def ejemplo(esquema, esquemas, campo="valor"):
    """Valor de forma válida con marcadores, nunca datos: el repositorio no lleva datos de ejemplo reales."""
    if "$ref" in esquema:
        return ejemplo(esquemas[esquema["$ref"].rsplit("/", 1)[1]], esquemas, campo)
    if "oneOf" in esquema:
        return ejemplo(esquema["oneOf"][0], esquemas, campo)
    if "enum" in esquema:
        return esquema["enum"][0]
    tipo = esquema["type"][0] if isinstance(esquema["type"], list) else esquema["type"]
    if tipo == "object":
        return {k: ejemplo(v, esquemas, k) for k, v in esquema["properties"].items()}
    if tipo == "array":
        return [ejemplo(esquema["items"], esquemas, campo)]
    if tipo == "number":
        return 1.0
    if tipo == "integer":
        return 1
    if tipo == "boolean":
        return False
    return {"uuid": "00000000-0000-0000-0000-000000000000", "date-time": "2000-01-01T00:00:00Z",
            "date": "2000-01-01"}.get(esquema.get("format"), f"<{campo}>")


def json_texto(valor):
    return json.dumps(valor, ensure_ascii=False, indent=2) + "\n"


def main():
    archivos = {}
    for modulo in CON_API:
        documento = openapi(modulo)
        archivos[SALIDA / f"openapi/{modulo}.yaml"] = "# Generado: no editar a mano.\n" + f.yaml(documento)
    documento, esquemas = asyncapi()
    archivos[SALIDA / "asyncapi.yaml"] = "# Generado: no editar a mano.\n" + f.yaml(documento)
    for nombre in documento["components"]["messages"]:
        archivos[SALIDA / f"ejemplos/{nombre}.json"] = json_texto(ejemplo(esquemas[nombre], esquemas))
    # HTTP entre Java (SrvRuteo) y .NET (WSPagoYoPago), paso VII.24
    usados = {}
    for registro in ("ReportePago", "ResultadoConciliacion"):
        registrar("mspago", registro, usados)
        archivos[SALIDA / f"ejemplos/{registro}.json"] = json_texto(ejemplo(usados[registro], usados))
    f.escribir_o_comprobar(archivos, "--comprobar" in sys.argv)


if __name__ == "__main__":
    main()

"""Lee de los fuentes lo que documentan los contratos y los README: rutas, cuerpos, colas y variables.

Nada se escribe a mano si se puede leer del código o de docs/modelo/modelo-canonico.json. Lo único
manual está en este archivo y en generar_*.py, en tablas que los generadores contrastan con el código.
"""
import json
import re
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
MODELO = json.loads((RAIZ / "docs/modelo/modelo-canonico.json").read_text(encoding="utf-8"))
JAVA_BASE = "src/main/java/co/edu/javeriana/sso"

# módulo -> (artefacto del diagrama, lenguaje, carpeta, servicio en deploy/dev/docker-compose.yml)
COMPONENTES = {
    "apigateway": ("APIGateway.jar", "java", "services/apigateway", "apigateway"),
    "msacceso": ("MSAcceso.jar", "java", "services/msacceso", "msacceso"),
    "mscatalogo": ("MSCatalogo.jar", "java", "services/mscatalogo", "mscatalogo"),
    "mssubasta": ("MSSubasta.jar", "java", "services/mssubasta", "mssubasta"),
    "mssuscripcion": ("MSSuscripcion.jar", "java", "services/mssuscripcion", "mssuscripcion"),
    "mscompra": ("MSCompra.jar", "java", "services/mscompra", "mscompra-replica1"),
    "msvalidacion": ("MSValidacion.jar", "java", "services/msvalidacion", "msvalidacion"),
    "msdespacho": ("MSDespacho.jar", "java", "services/msdespacho", "msdespacho"),
    "msnotificacion": ("MSNotificacion.jar", "java", "services/msnotificacion", "msnotificacion"),
    "ofacbackend": ("OFACBackEnd.jar", "java", "services/ofacbackend", "ofacbackend"),
    "sinbackend": ("SINBackEnd.jar", "java", "services/sinbackend", "sinbackend"),
    "senbackend": ("SENBackEnd.jar", "java", "services/senbackend", "senbackend"),
    "mspago": ("MSPago.dll", "dotnet", "services-dotnet/MSPago", "mspago"),
    "yopagobackend": ("YoPagoBackEnd.dll", "dotnet", "services-dotnet/YoPagoBackEnd", "yopagobackend"),
    "yopagofrontend": ("YoPagoFrontEnd.dll", "dotnet", "services-dotnet/YoPagoFrontEnd", "yopagofrontend"),
}

CODIGOS_HTTP = {
    "OK": 200, "CREATED": 201, "ACCEPTED": 202, "UNAUTHORIZED": 401, "FORBIDDEN": 403, "NOT_FOUND": 404,
    "CONFLICT": 409, "UNPROCESSABLE_CONTENT": 422, "SERVICE_UNAVAILABLE": 503,
    "Status401Unauthorized": 401, "Status404NotFound": 404, "Status409Conflict": 409,
}


def carpeta(modulo):
    return RAIZ / COMPONENTES[modulo][2]


def lenguaje(modulo):
    return COMPONENTES[modulo][1]


def _fuentes(modulo):
    if lenguaje(modulo) == "java":
        return sorted((carpeta(modulo) / JAVA_BASE / modulo).rglob("*.java"))
    return sorted(p for p in carpeta(modulo).rglob("*.cs") if not {"bin", "obj"} & set(p.parts))


def _sin_comentarios(texto):
    return re.sub(r"/\*.*?\*/", "", texto, flags=re.S)


def _partir(texto):
    """Separa por comas de primer nivel, sin cortar dentro de <>, () o comillas."""
    partes, actual, nivel, comillas = [], "", 0, False
    for c in texto:
        if c == '"':
            comillas = not comillas
        if not comillas:
            nivel += c in "<(["
            nivel -= c in ">)]"
            if c == "," and nivel == 0:
                partes.append(actual)
                actual = ""
                continue
        actual += c
    if actual.strip():
        partes.append(actual)
    return [p.strip() for p in partes]


def _cierre(texto, inicio):
    """Índice del paréntesis que cierra al que está en inicio."""
    nivel, comillas = 0, False
    for i in range(inicio, len(texto)):
        c = texto[i]
        if c == '"' and texto[i - 1] != "\\":
            comillas = not comillas
        elif not comillas:
            nivel += c == "("
            nivel -= c == ")"
            if nivel == 0:
                return i
    raise ValueError("Paréntesis sin cerrar")


def _param(texto, modulo):
    """'@NotBlank String idSubasta' → dict con nombre JSON, tipo, anotaciones y si es obligatorio."""
    anotaciones = {n: (a or "") for n, a in re.findall(r"@(\w+)(?:\(((?:[^()\"]|\"(?:[^\"\\]|\\.)*\")*)\))?", texto)}
    sin_anotaciones = re.sub(r"@\w+(?:\((?:[^()\"]|\"(?:[^\"\\]|\\.)*\")*\))?", "", texto).strip()
    tipo, nombre = sin_anotaciones.rsplit(None, 1)
    if lenguaje(modulo) == "dotnet":
        nulo = tipo.endswith("?")
        return {"nombre": nombre[0].lower() + nombre[1:], "tipo": tipo.rstrip("?"), "requerido": not nulo,
                "nulable": nulo, "anotaciones": anotaciones}
    return {"nombre": nombre, "tipo": tipo, "anotaciones": anotaciones, "nulable": False,
            "requerido": bool({"NotBlank", "NotNull"} & set(anotaciones))}


def tipos(modulo):
    """Records y enums del módulo. Si un nombre se repite, gana el record del archivo con ese nombre."""
    registros, enums = {}, {}
    for ruta in _fuentes(modulo):
        texto = _sin_comentarios(ruta.read_text(encoding="utf-8"))
        texto = re.sub(r"^\s*///.*$", "", texto, flags=re.M)
        for m in re.finditer(r"\brecord\s+(\w+)\s*\(", texto):
            nombre = m.group(1)
            if nombre in registros and ruta.stem != nombre:
                continue
            fin = _cierre(texto, m.end() - 1)
            registros[nombre] = [_param(p, modulo) for p in _partir(texto[m.end():fin])]
        for m in re.finditer(r"\benum\s+(\w+)\s*\{(.*?)\}", texto, flags=re.S):
            cuerpo = re.sub(r"//.*", "", m.group(2)).split(";")[0]
            enums[m.group(1)] = [l.strip() for l in cuerpo.split(",") if l.strip()]
    return registros, enums


def _javadoc_previo(texto, posicion):
    previo = texto[:posicion].rstrip()
    if not previo.endswith("*/"):
        return ""
    return re.sub(r"\s*\n\s*\*\s*", " ", previo[previo.rfind("/**") + 3:-2]).strip(" *\n")


def _ruta(*partes):
    ruta = "/" + "/".join(p.strip("/") for p in partes if p.strip("/"))
    return ruta


def _operaciones_java(modulo):
    ops = []
    for ruta in sorted((carpeta(modulo) / JAVA_BASE / modulo).glob("WS*.java")):
        texto = ruta.read_text(encoding="utf-8")
        base = re.search(r'@RequestMapping\("([^"]*)"\)', texto).group(1)
        for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?', texto):
            firma = re.compile(r"public\s+[\w<>\[\], ]+?\s+(\w+)\s*\(").search(texto, m.end())
            resto = texto[m.end():firma.start()]
            estado = re.search(r"@ResponseStatus\(HttpStatus\.(\w+)\)", resto)
            retorno = texto[firma.start():firma.end()].split()[1:-1]
            fin = _cierre(texto, firma.end() - 1)
            ops.append({
                "clase": ruta.stem,
                "metodo": m.group(1).lower(),
                "ruta": _ruta(base, m.group(2) or ""),
                "operacion": firma.group(1),
                "descripcion": _javadoc_previo(texto, m.start()),
                "estado": CODIGOS_HTTP[estado.group(1)] if estado else 200,
                "retorno": " ".join(retorno).replace("( ", "").split("(")[0],
                "parametros": [_param(p, modulo) for p in _partir(texto[firma.end():fin])],
            })
    return ops


def _operaciones_dotnet(modulo):
    ops = []
    for ruta in sorted(carpeta(modulo).glob("*.cs")):
        texto = ruta.read_text(encoding="utf-8")
        base = re.search(r'\[Route\("([^"]*)"\)\]', texto)
        if not base:
            continue
        for m in re.finditer(r'\[Http(Get|Post|Put|Delete|Patch)(?:\("([^"]*)"\))?\]', texto):
            firma = re.compile(r"public\s+(?:async\s+)?([\w<>?, ]+?)\s+(\w+)\s*\(").search(texto, m.end())
            fin = _cierre(texto, firma.end() - 1)
            comentario = re.search(r"///\s*<summary>(.*?)</summary>\s*$", texto[:m.start()])
            descripcion = comentario.group(1) if comentario else ""
            descripcion = re.sub(r'<paramref name="(\w+)"\s*/>', r"`\1`", descripcion)
            ops.append({
                "clase": ruta.stem,
                "metodo": m.group(1).lower(),
                "ruta": _ruta(base.group(1), m.group(2) or ""),
                "operacion": firma.group(2),
                "descripcion": descripcion,
                "estado": 200,
                "retorno": re.sub(r"^Task<(.*)>$", r"\1", firma.group(1)),
                "parametros": [_param(p, modulo) for p in _partir(texto[firma.end():fin])],
            })
    return ops


def operaciones(modulo):
    return _operaciones_java(modulo) if lenguaje(modulo) == "java" else _operaciones_dotnet(modulo)


def errores(modulo):
    """Excepción -> código HTTP, leído de los manejadores de errores del módulo."""
    resultado = {}
    if lenguaje(modulo) == "java":
        for ruta in (carpeta(modulo) / JAVA_BASE / modulo).rglob("ManejoErrores.java"):
            texto = ruta.read_text(encoding="utf-8")
            for bloque in re.split(r"(?=@ExceptionHandler)", texto)[1:]:
                nombre = re.search(r"@ExceptionHandler\((\w+)\.class\)", bloque).group(1)
                resultado[nombre] = CODIGOS_HTTP[re.search(r"HttpStatus\.(\w+)", bloque).group(1)]
    else:
        for ruta in carpeta(modulo).rglob("Configuracion.cs"):
            for nombre, codigo in re.findall(r"(\w+Exception) =>.*?StatusCodes\.(\w+)", ruta.read_text(encoding="utf-8")):
                resultado[nombre] = CODIGOS_HTTP[codigo]
    return resultado


def consumidas(modulo):
    """Cola real (incluida la variante de q.LiberarBienSubasta) -> clase Listener."""
    resultado = {}
    for ruta in _fuentes(modulo):
        if not ruta.stem.startswith("Listener"):
            continue
        texto = ruta.read_text(encoding="utf-8")
        declaradas = re.search(r"@RabbitListener\(queues = (.*?)\)", texto)
        if declaradas:
            for cola in re.findall(r'"([^"]+)"', declaradas.group(1)):
                resultado[cola] = ruta.stem
        for cola in re.findall(r'const string Cola = "([^"]+)"', texto):
            resultado[cola] = ruta.stem
    return resultado


def producidas(modulo):
    """Lista de (cola, record, Worker) que el módulo guarda en su outbox y publica."""
    resultado = []
    worker = next((p.stem for p in _fuentes(modulo) if p.stem.startswith("WorkerOutbox")), None)
    for ruta in _fuentes(modulo):
        if "modelo" not in ruta.parts:
            continue
        texto = _sin_comentarios(ruta.read_text(encoding="utf-8"))
        if lenguaje(modulo) == "java":
            cola = re.search(r'return "(q\.[^"]+)"', texto)
            if cola:
                resultado.append((cola.group(1), ruta.stem, worker))
        else:
            for m in re.finditer(r"\brecord\s+(\w+)\s*\(", texto):
                cuerpo = texto[_cierre(texto, m.end() - 1):]
                cola = re.match(r'[^;]*?\{\s*\[JsonIgnore\] public string Cola => "(q\.[^"]+)"', cuerpo)
                if cola:
                    resultado.append((cola.group(1), m.group(1), worker))
    return sorted(resultado)


def variables(modulo):
    """Variable de entorno -> dónde se usa (propiedad de application.yml o campo de Program.cs)."""
    resultado = {}
    if lenguaje(modulo) == "java":
        pila = []
        ruta = carpeta(modulo) / "src/main/resources/application.yml"
        for linea in ruta.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^(\s*)([\w.-]+):\s*(.*)$", linea)
            if not m or linea.lstrip().startswith("#"):
                continue
            nivel = len(m.group(1))
            while pila and pila[-1][0] >= nivel:
                pila.pop()
            pila.append((nivel, m.group(2)))
            for variable in re.findall(r"\$\{(\w+)\}", m.group(3)):
                resultado[variable] = ".".join(p for _, p in pila)
        return resultado
    for ruta in _fuentes(modulo):
        texto = ruta.read_text(encoding="utf-8")
        for m in re.finditer(r'(?:(\w+)\s*=\s*)?(?:Entorno\.Requerida|Environment\.GetEnvironmentVariable)\("(\w+)"\)', texto):
            if m.group(2) not in resultado or m.group(1):
                resultado[m.group(2)] = m.group(1) or ruta.name
    return resultado


def entorno_compose(servicio):
    """Bloque environment de un servicio en deploy/dev/docker-compose.yml."""
    lineas = (RAIZ / "deploy/dev/docker-compose.yml").read_text(encoding="utf-8").splitlines()
    inicio = lineas.index(f"  {servicio}:")
    resultado, dentro = {}, False
    for linea in lineas[inicio + 1:]:
        if re.match(r"^  \S", linea) or not linea.strip():
            break
        if linea == "    environment:":
            dentro = True
        elif dentro and (m := re.match(r"^      (\w+): (.*)$", linea)):
            resultado[m.group(1)] = re.sub(r"\$\{(\w+):\?[^}]*\}", r"${\1}", m.group(2))
        elif dentro:
            dentro = False
    return resultado


def componente_modelo(artefacto):
    return MODELO["componentes"][artefacto]


def amqp_del_diagrama():
    """(productores, consumidores) por cola, según las flechas AMQP del diagrama."""
    productores, consumidores = {}, {}
    for c in MODELO["conexiones"]:
        if c["protocolo"] != "AMQP":
            continue
        if c["a"].startswith("q."):
            productores.setdefault(c["a"], set()).add(c["de"])
        else:
            consumidores.setdefault(c["de"], set()).add(c["a"])
    return productores, consumidores


def yaml(valor, nivel=0):
    """YAML sin dependencias. Las cadenas dudosas salen como JSON, que también es YAML válido."""
    sangria = "  " * nivel
    if isinstance(valor, dict):
        if not valor:
            return " {}\n"
        salida = "\n" if nivel else ""
        for clave, v in valor.items():
            salida += f"{sangria}{_escalar(str(clave))}:{yaml(v, nivel + 1)}"
        return salida
    if isinstance(valor, list):
        if not valor:
            return " []\n"
        salida = "\n"
        for v in valor:
            cuerpo = yaml(v, nivel + 1)
            if isinstance(v, dict) and v:
                salida += f"{sangria}- {cuerpo.lstrip()}"
            elif isinstance(v, list) and v:
                salida += f"{sangria}-{cuerpo}"
            else:
                salida += f"{sangria}-{cuerpo}"
        return salida
    return f" {_escalar(valor)}\n"


def _escalar(valor):
    if valor is None:
        return "null"
    if isinstance(valor, bool):
        return "true" if valor else "false"
    if isinstance(valor, (int, float)):
        return str(valor)
    reservada = valor.lower() in {"true", "false", "null", "yes", "no", "on", "off", "y", "n", "~"}
    if re.fullmatch(r"[A-Za-zÁÉÍÓÚáéíóúÑñ_][\wÁÉÍÓÚáéíóúÑñ .,/·-]*", valor) and not reservada and valor == valor.strip():
        return valor
    return json.dumps(valor, ensure_ascii=False)


def escribir_o_comprobar(archivos, comprobar):
    """Escribe los archivos o, con comprobar, falla si alguno difiere de lo generado."""
    distintos = []
    for ruta, contenido in archivos.items():
        if comprobar:
            if not ruta.exists() or ruta.read_text(encoding="utf-8") != contenido:
                distintos.append(ruta.relative_to(RAIZ))
        else:
            ruta.parent.mkdir(parents=True, exist_ok=True)
            ruta.write_text(contenido, encoding="utf-8")
    if distintos:
        raise SystemExit("✗ Desactualizados (regenerar): " + ", ".join(map(str, distintos)))
    print(f"✓ {len(archivos)} archivos {'al día' if comprobar else 'generados'}")

"""Extrae el modelo canónico del proyecto SSO: lo que el código debe respetar al pie de la letra.

Única fuente de verdad: el diagrama de despliegue «Arquitectura Integrada JavaEE-.NET» del VPP
y los pasos de los casos de uso (PDF) que se dibujan sobre ese diagrama. El diagrama EBC, el
«Diagrama Fisico MS-SSO», el de componentes y los diagramas DS están desactualizados (no tienen
.NET) y NO se usan.

Genera docs/modelo/modelo-canonico.json con:
  componentes  artefacto -> nodos (nodo, puerto, máquina, IP) y clases que contiene
  conexiones   flechas con protocolo del diagrama (HTTP, AMQP, JDBC, LDAP, SMTP…)
  pasos        cada paso del PDF: id, CU, origen, destino, mensaje, operaciones, notas
  operaciones  operaciones que recibe cada clase (unión de los pasos)
  validacion   pasos del PDF comparados con las flechas numeradas del VPP con secuencias

Uso: python3 -I tools/vpp/extraer_modelo.py
"""
import json
import re
import sqlite3
import subprocess
from collections import defaultdict
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
ENUNCIADO = RAIZ / "docs/enunciado"
VPP = ENUNCIADO / "ProyectoSSO-Integrada-JavaEE-NET.vpp"
VPP_SECUENCIAS = ENUNCIADO / "ProyectoSSO-Integrada-JavaEE-NET-con-secuencias.vpp"
PDF = ENUNCIADO / "casos-de-uso-arquitectura-integrada.pdf"
SALIDA = RAIZ / "docs/modelo/modelo-canonico.json"
DIAGRAMA = "Arquitectura Integrada%"

CU = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
PASO = re.compile(r"^\s*((?:%s)\.\d+[a-z]?(?:/[a-z])?m?)\s+(.+?)\s*→\s*(.+?)\s{2,}(.+)$" % "|".join(CU))
# Destinos que no son clases de código propio
NO_CODIGO = re.compile(r"^(Browser|Nginx|AD|CORREO|SMS GATEWAY|q\..+|DB .+)$")


def leer(vpp, sql, *args):
    con = sqlite3.connect(f"file:{vpp}?mode=ro", uri=True)
    try:
        return con.execute(sql, args).fetchall()
    finally:
        con.close()


def arquitectura():
    filas = leer(VPP, """
        select e.ID, e.PARENT_ID, m.ID, m.MODEL_TYPE, m.NAME, m.DEFINITION
        from DIAGRAM_ELEMENT e join DIAGRAM d on e.DIAGRAM_ID = d.ID
        join MODEL_ELEMENT m on e.MODEL_ELEMENT_ID = m.ID where d.NAME like ?""", DIAGRAMA)
    forma = {f[0]: f for f in filas}
    nombre_modelo = {f[2]: f[4] for f in filas}

    def padre(eid, tipo):
        actual = forma.get(eid)
        while actual and actual[1] in forma:
            actual = forma[actual[1]]
            if actual[3] == tipo:
                return actual
        return None

    estereotipo = dict(leer(VPP, "select ID, NAME from MODEL_ELEMENT where MODEL_TYPE = 'Stereotype'"))

    def estereotipos(fila):
        texto = fila[5].decode("utf-8", "replace") if isinstance(fila[5], bytes) else fila[5]
        bloque = re.search(r"stereotypes=\(([^)]*)\)", texto)
        return [estereotipo[i] for i in re.findall(r"<([^>]+)>", bloque.group(1)) if i in estereotipo] if bloque else []

    def nodo_info(nodo, maquina):
        puerto = re.search(r":(\d+)\s*$", nodo[4] if nodo else "")
        ip = re.search(r"(\d+\.\d+\.\d+\.\d+)", maquina[4] if maquina else "")
        return {"nodo": nodo[4] if nodo else None, "puerto": int(puerto.group(1)) if puerto else None,
                "tecnologia": estereotipos(nodo) if nodo else [],
                "maquina": maquina[4] if maquina else None, "ip": ip.group(1) if ip else None}

    componentes = {}
    for eid, _, _, tipo, n, _ in filas:
        if tipo == "Component":
            nodo = padre(eid, "Node")
            maquina = padre(nodo[0], "Node") if nodo else None
            c = componentes.setdefault(n, {"nodos": [], "clases": set()})
            c["nodos"].append(nodo_info(nodo, maquina))
    for eid, p, _, tipo, n, _ in filas:
        if tipo == "Class" and p in forma and forma[p][3] == "Component":
            componentes[forma[p][4]]["clases"].add(n)

    infraestructura = []
    for fila in filas:
        eid, tipo = fila[0], fila[3]
        if tipo == "Node" and not any(f[1] == eid and f[3] in ("Component", "Node") for f in filas):
            maquina = padre(eid, "Node")
            if maquina:
                info = nodo_info(fila, maquina)
                info["contiene"] = sorted(f[4] for f in filas if f[1] == eid and f[3] == "Class")
                infraestructura.append(info)

    conexiones = []
    for _, _, _, tipo, n, d in filas:
        if tipo == "Dependency" and n:
            texto = d.decode("utf-8", "replace") if isinstance(d, bytes) else d
            de = re.search(r"fromModel=<([^>]+)>", texto)
            a = re.search(r"toModel=<([^>]+)>", texto)
            if de and a:
                conexiones.append({"protocolo": n,
                                   "de": nombre_modelo.get(de.group(1).split(":")[-1], "?"),
                                   "a": nombre_modelo.get(a.group(1).split(":")[-1], "?")})
    conexiones.sort(key=lambda c: (c["protocolo"], c["de"], c["a"]))

    return ({k: {"nodos": v["nodos"], "clases": sorted(v["clases"])} for k, v in sorted(componentes.items())},
            sorted(infraestructura, key=lambda x: (x["maquina"], x["nodo"])), conexiones)


def operaciones_de(mensaje):
    ops = []
    for parte in re.split(r"\s+[/·]\s+", mensaje):
        m = re.match(r"([a-z][A-Za-z0-9]*)\b", parte.strip())
        if m:
            ops.append(m.group(1))
    return ops


def pasos_pdf():
    texto = subprocess.run(["pdftotext", "-layout", str(PDF), "-"], check=True, capture_output=True, text=True).stdout
    pasos, ultimo = [], None
    for linea in texto.splitlines():
        m = PASO.match(linea)
        if m:
            pid, de, a, resto = m.groups()
            columnas = re.split(r"\s{2,}", resto.strip())
            ultimo = {"id": pid, "cu": pid.split(".")[0], "de": de.strip(), "a": a.strip(),
                      "mensaje": columnas[0], "notas": columnas[1:]}
            pasos.append(ultimo)
        elif ultimo and linea.strip() and not re.match(r"^\s*(\d+|[IVX]+\s|DS\d|Casos|SSO|JavaEE)", linea.strip()) \
                and len(linea) - len(linea.lstrip()) > 20:
            # continuación del mensaje o de las notas en la línea siguiente
            extra = re.split(r"\s{2,}", linea.strip())
            ultimo["notas"].extend(extra)
            ultimo = None
    for p in pasos:
        p["operaciones"] = operaciones_de(p["mensaje"])
    return pasos


def flechas_numeradas():
    if not VPP_SECUENCIAS.exists():
        return {}
    filas = leer(VPP_SECUENCIAS, """
        select m.NAME, m.DEFINITION from DIAGRAM_ELEMENT e join DIAGRAM d on e.DIAGRAM_ID = d.ID
        join MODEL_ELEMENT m on e.MODEL_ELEMENT_ID = m.ID
        where d.NAME like ? and m.MODEL_TYPE = 'Dependency'""", DIAGRAMA)
    nombres = dict(leer(VPP_SECUENCIAS, "select ID, NAME from MODEL_ELEMENT"))
    flechas = {}
    for n, d in filas:
        if not re.match(r"^(%s)\." % "|".join(CU), n or ""):
            continue
        texto = d.decode("utf-8", "replace") if isinstance(d, bytes) else d
        de = re.search(r"fromModel=<([^>]+)>", texto).group(1).split(":")[-1]
        a = re.search(r"toModel=<([^>]+)>", texto).group(1).split(":")[-1]
        for pid in re.split(r"[,\s]+", n.strip()):
            flechas[pid] = (nombres.get(de, "?"), nombres.get(a, "?"))
    return flechas


def main():
    componentes, infraestructura, conexiones = arquitectura()
    pasos = pasos_pdf()

    operaciones = defaultdict(set)
    for p in pasos:
        if not NO_CODIGO.match(p["a"]):
            operaciones[p["a"]].update(p["operaciones"])

    flechas = flechas_numeradas()
    def ids_vpp(pid):
        """El PDF escribe 'V.8a/b'; el VPP dibuja una flecha por réplica: V.8a y V.8b."""
        m = re.match(r"^(.+?)([a-z])/([a-z])$", pid)
        return [m.group(1) + m.group(2), m.group(1) + m.group(3)] if m else [pid]

    diferencias, cubiertas = [], set()
    for p in pasos:
        for vid in ids_vpp(p["id"]):
            cubiertas.add(vid)
            f = flechas.get(vid)
            if f is None:
                diferencias.append({"paso": vid, "problema": "sin flecha en el VPP con secuencias"})
            elif f != (p["de"], p["a"]):
                diferencias.append({"paso": vid, "problema": f"VPP {f[0]} → {f[1]} vs PDF {p['de']} → {p['a']}"})
    sobrantes = sorted(set(flechas) - cubiertas)

    clases_vpp = {c for v in componentes.values() for c in v["clases"]}
    sin_componente = sorted({x for p in pasos for x in (p["de"], p["a"])} - clases_vpp
                            - {p["a"] for p in pasos if NO_CODIGO.match(p["a"])}
                            - {p["de"] for p in pasos if NO_CODIGO.match(p["de"])})

    salida = {
        "fuente": {"diagrama": "Arquitectura Integrada JavaEE-.NET", "vpp": VPP.name, "pdf": PDF.name},
        "componentes": componentes,
        "infraestructura": infraestructura,
        "conexiones": conexiones,
        "operaciones": {k: sorted(v) for k, v in sorted(operaciones.items())},
        "pasos": pasos,
        "validacion": {"flechas_vpp": len(flechas), "pasos_pdf": len(pasos),
                       "diferencias": diferencias, "flechas_sin_paso": sobrantes,
                       "clases_en_pasos_sin_componente": sin_componente},
    }
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text(json.dumps(salida, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{SALIDA.relative_to(RAIZ)}: {len(componentes)} componentes, {len(pasos)} pasos, "
          f"{len(flechas)} flechas VPP, {len(diferencias)} diferencias")


if __name__ == "__main__":
    main()

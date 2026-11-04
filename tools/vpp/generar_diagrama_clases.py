#!/usr/bin/env python3
"""Genera docs/modelo/diagrama-clases.{dot,svg,pdf}: todas las clases del diagrama integrado, por componente.

Sale de docs/modelo/modelo-canonico.json, con los colores por sistema del modelo. Requiere Graphviz (`dot`).
Sin SSOMobile.apk (observación 15). Uso: python3 -I tools/vpp/generar_diagrama_clases.py
"""
import json
import subprocess
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SALIDA = RAIZ / "docs/modelo/diagrama-clases"
COLORES = {
    "SSO": "#BBDEFB",
    "YoPago": "#F8BBD0",
    "OFAC": "#FFE0B2",
    "SIN": "#B3E5FC",
    "SEN": "#B2EBF2",
    "MOM": "#FFF59D",
    "Externo": "#E0E0E0",
}
SISTEMA = {
    "MSPago.dll": "YoPago",
    "YoPagoBackEnd.dll": "YoPago",
    "YoPagoFrontEnd.dll": "YoPago",
    "OFACBackEnd.jar": "OFAC",
    "SINBackEnd.jar": "SIN",
    "SENBackEnd.jar": "SEN",
}
EXCLUIDOS = {"SSOMobile.apk"}


def escapar(texto):
    return str(texto).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def nodo_id(nombre):
    return '"' + str(nombre).replace('"', "") + '"'


def etiqueta(clase, operaciones):
    filas = "".join(f'<TR><TD ALIGN="LEFT">{escapar(o)}</TD></TR>' for o in operaciones)
    cuerpo = filas or '<TR><TD ALIGN="LEFT"> </TD></TR>'
    return (
        '<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" BGCOLOR="white">'
        f"<TR><TD><B>{escapar(clase)}</B></TD></TR><HR/>{cuerpo}</TABLE>>"
    )


def main():
    modelo = json.loads((RAIZ / "docs/modelo/modelo-canonico.json").read_text(encoding="utf-8"))
    lineas = [
        "digraph clases {",
        '  graph [rankdir=LR, fontname="Helvetica", labelloc=t, fontsize=30, nodesep=0.25, ranksep=1.2, '
        'label="SSO · Todas las clases por componente (Arquitectura Integrada JavaEE-.NET)"];',
        '  node [shape=plain, fontname="Helvetica", fontsize=11];',
        '  edge [fontname="Helvetica", fontsize=9, color="#546E7A"];',
    ]
    clases = set()
    for i, (componente, datos) in enumerate(sorted(modelo["componentes"].items())):
        if componente in EXCLUIDOS:
            continue
        color = COLORES[SISTEMA.get(componente, "SSO")]
        lineas += [
            f"  subgraph cluster_{i} {{",
            f'    label="{componente}"; style=filled; fillcolor="{color}"; fontsize=18;',
        ]
        for clase in datos["clases"]:
            clases.add(clase)
            operaciones = modelo["operaciones"].get(clase, [])
            lineas.append(f"    {nodo_id(clase)} [label={etiqueta(clase, operaciones)}];")
        lineas.append("  }")
    vistos = set()
    externos = set()
    for conexion in modelo["conexiones"]:
        de, a, protocolo = conexion["de"], conexion["a"], conexion["protocolo"]
        if "Mobile" in de or "Mobile" in a or (de, a, protocolo) in vistos:
            continue
        vistos.add((de, a, protocolo))
        externos |= {n for n in (de, a) if n not in clases}
        lineas.append(f'  {nodo_id(de)} -> {nodo_id(a)} [label="{protocolo}"];')
    for nombre in sorted(externos):
        color = COLORES["MOM"] if nombre.startswith("q.") else COLORES["Externo"]
        lineas.append(f'  {nodo_id(nombre)} [shape=box, style=filled, fillcolor="{color}", fontsize=12];')
    lineas.append("}")
    dot = SALIDA.with_suffix(".dot")
    dot.write_text("\n".join(lineas) + "\n", encoding="utf-8")
    for formato in ("svg", "pdf"):
        subprocess.run(["dot", f"-T{formato}", str(dot), "-o", str(SALIDA.with_suffix("." + formato))], check=True)
    print(f"✓ {len(clases)} clases, {len(vistos)} conexiones: {SALIDA.relative_to(RAIZ)}.dot/.svg/.pdf")


if __name__ == "__main__":
    main()

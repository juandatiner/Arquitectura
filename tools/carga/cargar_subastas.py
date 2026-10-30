#!/usr/bin/env python3
"""Carga los bienes incautados y sus subastas al desplegar, en DB CATALOGO (PENDIENTE) y DB SUBASTA
(PROGRAMADA) con los mismos identificadores, valor mínimo y fechas (observación 9).

El CSV vive en la VM (nunca en el repositorio). Columnas: ver tools/carga/plantillas/subastas.csv.
Fechas en ISO-8601 con zona (ej. 2026-11-20T08:00:00-05:00). Idempotente: una subasta que ya existe no
se modifica.

Uso:
  python3 -I tools/carga/cargar_subastas.py /ruta/subastas.csv

Variables (obligatorias):
  DB_CATALOGO_CONTENEDOR  contenedor de DB CATALOGO
  DB_SUBASTA_CONTENEDOR   contenedor de DB SUBASTA
"""
import os
import re
import sys
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from comun import ejecutar_sql, fallar, leer_csv, sql_texto  # noqa: E402

COLUMNAS = ["id_subasta", "id_bien", "descripcion", "categoria", "valor_minimo", "fecha_inicio", "fecha_cierre"]


def validar(filas):
    errores, ids = [], set()
    for n, f in filas:
        if not re.fullmatch(r"[A-Za-z0-9-]{1,20}", f["id_subasta"]) or not re.fullmatch(r"[A-Za-z0-9-]{1,20}", f["id_bien"]):
            errores.append(f"fila {n}: id_subasta / id_bien inválidos")
        if not f["descripcion"].strip() or not re.fullmatch(r"[A-Z_]{1,40}", f["categoria"]):
            errores.append(f"fila {n}: descripción obligatoria y categoría en MAYÚSCULAS")
        try:
            if Decimal(f["valor_minimo"]) <= 0:
                raise InvalidOperation
        except InvalidOperation:
            errores.append(f"fila {n}: valor_minimo debe ser un número mayor que cero")
        try:
            inicio, cierre = datetime.fromisoformat(f["fecha_inicio"]), datetime.fromisoformat(f["fecha_cierre"])
            if inicio.tzinfo is None or cierre.tzinfo is None:
                errores.append(f"fila {n}: las fechas deben incluir zona horaria")
            elif cierre <= inicio:
                errores.append(f"fila {n}: fecha_cierre debe ser posterior a fecha_inicio")
        except ValueError:
            errores.append(f"fila {n}: fechas en formato ISO-8601")
        if f["id_subasta"] in ids:
            errores.append(f"fila {n}: id_subasta repetido")
        ids.add(f["id_subasta"])
    fallar(errores)


def variable(nombre):
    return os.environ.get(nombre) or sys.exit(f"Falta la variable {nombre}")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    filas = leer_csv(sys.argv[1], COLUMNAS)
    validar(filas)
    t = {c: lambda f, c=c: sql_texto(f[c]) for c in COLUMNAS}
    bienes = ",\n".join(f"({t['id_bien'](f)}, {t['descripcion'](f)}, {t['categoria'](f)}, {t['valor_minimo'](f)})" for _, f in filas)
    sub_cat = ",\n".join(
        f"({t['id_subasta'](f)}, {t['id_bien'](f)}, {t['fecha_inicio'](f)}, {t['fecha_cierre'](f)}, 'PENDIENTE')" for _, f in filas)
    sub_sub = ",\n".join(
        f"({t['id_subasta'](f)}, {t['id_bien'](f)}, {t['descripcion'](f)}, {t['valor_minimo'](f)}, "
        f"{t['fecha_inicio'](f)}, {t['fecha_cierre'](f)}, 'PROGRAMADA')" for _, f in filas)
    ejecutar_sql(variable("DB_CATALOGO_CONTENEDOR"), "catalogo", "catalogo", f"""
        INSERT INTO bien (id_bien, descripcion, categoria, valor_minimo) VALUES
        {bienes}
        ON CONFLICT (id_bien) DO NOTHING;
        INSERT INTO subasta (id_subasta, id_bien, fecha_inicio, fecha_cierre, estado) VALUES
        {sub_cat}
        ON CONFLICT (id_subasta) DO NOTHING;
    """)
    ejecutar_sql(variable("DB_SUBASTA_CONTENEDOR"), "subasta", "subasta", f"""
        INSERT INTO subasta (id_subasta, id_bien, descripcion, valor_minimo, fecha_inicio, fecha_cierre, estado) VALUES
        {sub_sub}
        ON CONFLICT (id_subasta) DO NOTHING;
    """)
    print(f"{len(filas)} subastas cargadas en DB CATALOGO (PENDIENTE) y DB SUBASTA (PROGRAMADA)")


if __name__ == "__main__":
    main()

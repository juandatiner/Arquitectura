#!/usr/bin/env python3
"""Carga la lista de la OFAC (sistema externo) al desplegar: personas REPORTADAS o EN_ESTUDIO en DB OFAC.

Una cédula que no aparece en la lista está habilitada para comprar (APROBADO). El CSV vive en la VM
(nunca en el repositorio). Columnas: ver tools/carga/plantillas/ofac.csv. Idempotente: si la cédula ya
está, se actualizan su estado y motivo.

Uso (Máquina 3):
  DB_OFAC_CONTENEDOR=<contenedor> python3 -I tools/carga/cargar_ofac.py /ruta/ofac.csv
"""
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from comun import ejecutar_sql, fallar, leer_csv, sql_texto  # noqa: E402

COLUMNAS = ["cedula", "estado", "motivo"]


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    contenedor = os.environ.get("DB_OFAC_CONTENEDOR") or sys.exit("Falta la variable DB_OFAC_CONTENEDOR")
    filas = leer_csv(sys.argv[1], COLUMNAS)
    errores, cedulas = [], set()
    for n, f in filas:
        if not re.fullmatch(r"\d{5,15}", f["cedula"]):
            errores.append(f"fila {n}: cédula inválida")
        if f["estado"] not in ("REPORTADO", "EN_ESTUDIO"):
            errores.append(f"fila {n}: estado debe ser REPORTADO o EN_ESTUDIO")
        if f["cedula"] in cedulas:
            errores.append(f"fila {n}: cédula repetida")
        cedulas.add(f["cedula"])
    fallar(errores)
    valores = ",\n".join(f"({sql_texto(f['cedula'])}, {sql_texto(f['estado'])}, {sql_texto(f['motivo'])})" for _, f in filas)
    ejecutar_sql(contenedor, "ofac", "ofac", f"""
        INSERT INTO reporte (cedula, estado, motivo) VALUES
        {valores}
        ON CONFLICT (cedula) DO UPDATE SET estado = EXCLUDED.estado, motivo = EXCLUDED.motivo;
    """)
    print(f"DB OFAC: {len(filas)} personas en la lista")


if __name__ == "__main__":
    main()

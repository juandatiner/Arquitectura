"""Utilidades de los scripts de carga: leer el CSV, validar filas y ejecutar SQL/LDIF en los contenedores."""
import csv
import subprocess
import sys
from pathlib import Path


def leer_csv(ruta, columnas):
    """Lee el CSV y verifica que tenga exactamente las columnas de la plantilla."""
    with Path(ruta).open(encoding="utf-8", newline="") as archivo:
        lector = csv.DictReader(archivo)
        if lector.fieldnames != columnas:
            sys.exit(f"{ruta}: columnas {lector.fieldnames}, se esperaban {columnas} (ver tools/carga/plantillas)")
        return [(n, fila) for n, fila in enumerate(lector, start=2)]


def fallar(errores):
    if errores:
        print("\n".join(errores), file=sys.stderr)
        sys.exit(f"{len(errores)} errores en el archivo: no se cargó nada")


def sql_texto(valor):
    """Literal SQL seguro para un texto (comillas simples duplicadas)."""
    return "'" + str(valor).replace("'", "''") + "'"


def ejecutar_sql(contenedor, usuario, base, sql):
    """Ejecuta el SQL dentro del contenedor PostgreSQL en una sola transacción."""
    subprocess.run(
        ["docker", "exec", "-i", contenedor, "psql", "-v", "ON_ERROR_STOP=1", "-q", "-1", "-U", usuario, "-d", base],
        input=sql, text=True, check=True)

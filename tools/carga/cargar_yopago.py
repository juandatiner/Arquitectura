#!/usr/bin/env python3
"""Carga los usuarios de YoPago (sistema externo) al desplegar: el cliente paga identificándose con su
cédula y contraseña (enunciado). La clave se guarda con hash PBKDF2-SHA256, el mismo formato que verifica
YoPagoBackEnd (Claves.Verificar).

El CSV vive en la VM (nunca en el repositorio). Columnas: ver tools/carga/plantillas/yopago.csv.
Idempotente: si la cédula ya existe, se actualizan su nombre y su clave.

Uso (Máquina 3):
  DB_YOPAGO_CONTENEDOR=<contenedor> python3 -I tools/carga/cargar_yopago.py /ruta/yopago.csv
"""
import base64
import hashlib
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from comun import ejecutar_sql, fallar, leer_csv, sql_texto  # noqa: E402

COLUMNAS = ["cedula", "nombre", "clave"]
ITERACIONES = 100_000


def hash_clave(clave):
    sal = os.urandom(16)
    calculado = hashlib.pbkdf2_hmac("sha256", clave.encode(), sal, ITERACIONES)
    return f"pbkdf2_sha256${ITERACIONES}${base64.b64encode(sal).decode()}${base64.b64encode(calculado).decode()}"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    contenedor = os.environ.get("DB_YOPAGO_CONTENEDOR") or sys.exit("Falta la variable DB_YOPAGO_CONTENEDOR")
    filas = leer_csv(sys.argv[1], COLUMNAS)
    errores, cedulas = [], set()
    for n, f in filas:
        if not re.fullmatch(r"\d{5,15}", f["cedula"]):
            errores.append(f"fila {n}: cédula inválida")
        if not f["nombre"].strip():
            errores.append(f"fila {n}: nombre obligatorio")
        if len(f["clave"]) < 8:
            errores.append(f"fila {n}: la clave debe tener al menos 8 caracteres")
        if f["cedula"] in cedulas:
            errores.append(f"fila {n}: cédula repetida")
        cedulas.add(f["cedula"])
    fallar(errores)
    valores = ",\n".join(f"({sql_texto(f['cedula'])}, {sql_texto(f['nombre'])}, {sql_texto(hash_clave(f['clave']))})" for _, f in filas)
    ejecutar_sql(contenedor, "yopago", "yopago", f"""
        INSERT INTO usuario (cedula, nombre, clave_hash) VALUES
        {valores}
        ON CONFLICT (cedula) DO UPDATE SET nombre = EXCLUDED.nombre, clave_hash = EXCLUDED.clave_hash;
    """)
    print(f"DB YOPAGO: {len(filas)} usuarios de YoPago")


if __name__ == "__main__":
    main()

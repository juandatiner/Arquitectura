#!/usr/bin/env python3
"""Carga los usuarios de SSO al desplegar: los crea en AD (OpenLDAP) y registra sus datos en DB ACCESO.

El CSV vive en la VM (nunca en el repositorio). Columnas: ver tools/carga/plantillas/usuarios.csv.
Idempotente: un usuario que ya existe no se duplica (en DB ACCESO se actualizan sus datos).

Uso (AD está en Máquina 3 y DB ACCESO en Máquina 2: se corre en cada una con su destino):
  python3 -I tools/carga/cargar_usuarios.py /ruta/usuarios.csv --destino ad       # Máquina 3
  python3 -I tools/carga/cargar_usuarios.py /ruta/usuarios.csv --destino acceso   # Máquina 2
  python3 -I tools/carga/cargar_usuarios.py /ruta/usuarios.csv --destino todo     # todo en una máquina

Variables (obligatorias según el destino):
  AD_CONTENEDOR, AD_CLAVE_ADMIN   contenedor de AD SERVER y clave del administrador de OpenLDAP
  DB_ACCESO_CONTENEDOR            contenedor de DB ACCESO
"""
import base64
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from comun import ejecutar_sql, fallar, leer_csv, sql_texto  # noqa: E402

COLUMNAS = ["usuario", "cedula", "nombre", "celular", "correo", "direccion", "rol", "clave"]
BASE = "dc=sso,dc=javeriana,dc=edu,dc=co"


def ssha(clave):
    """Hash {SSHA} de OpenLDAP: la clave nunca se guarda en claro en AD."""
    sal = os.urandom(8)
    return "{SSHA}" + base64.b64encode(hashlib.sha1(clave.encode() + sal).digest() + sal).decode()


def validar(filas):
    errores, usuarios, cedulas = [], set(), set()
    for n, f in filas:
        if not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", f["usuario"]):
            errores.append(f"fila {n}: usuario inválido")
        if not re.fullmatch(r"\d{5,15}", f["cedula"]):
            errores.append(f"fila {n}: cédula inválida")
        if not re.fullmatch(r"\+?\d{7,15}", f["celular"]):
            errores.append(f"fila {n}: celular inválido")
        if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", f["correo"]):
            errores.append(f"fila {n}: correo inválido")
        if f["rol"] not in ("CLIENTE", "ADMIN"):
            errores.append(f"fila {n}: rol debe ser CLIENTE o ADMIN")
        if len(f["clave"]) < 8:
            errores.append(f"fila {n}: la clave debe tener al menos 8 caracteres")
        if not f["nombre"].strip() or not f["direccion"].strip():
            errores.append(f"fila {n}: nombre y dirección son obligatorios")
        if f["usuario"] in usuarios or f["cedula"] in cedulas:
            errores.append(f"fila {n}: usuario o cédula repetidos")
        usuarios.add(f["usuario"])
        cedulas.add(f["cedula"])
    fallar(errores)


def cargar_ad(filas, contenedor, clave_admin):
    for _, f in filas:
        dn = f"uid={f['usuario']},ou=usuarios,{BASE}"
        apellido = f["nombre"].split()[-1]
        ldif = (f"dn: {dn}\nobjectClass: inetOrgPerson\nuid: {f['usuario']}\ncn: {f['nombre']}\nsn: {apellido}\n"
                f"mail: {f['correo']}\nmobile: {f['celular']}\nemployeeNumber: {f['cedula']}\n"
                f"userPassword: {ssha(f['clave'])}\n")
        r = subprocess.run(
            ["docker", "exec", "-i", contenedor, "ldapadd", "-x", "-H", "ldap://localhost",
             "-D", f"cn=admin,{BASE}", "-w", clave_admin],
            input=ldif, text=True, capture_output=True)
        if r.returncode == 68:  # ya existe
            print(f"AD: {f['usuario']} ya existía")
        elif r.returncode != 0:
            sys.exit(f"AD: no se pudo crear {f['usuario']}: {r.stderr.strip()}")
        else:
            print(f"AD: {f['usuario']} creado")


def cargar_db_acceso(filas, contenedor):
    valores = ",\n".join(
        "(" + ", ".join(sql_texto(f[c]) for c in COLUMNAS[:-1]) + ")" for _, f in filas)
    ejecutar_sql(contenedor, "acceso", "acceso", f"""
        INSERT INTO cliente (usuario, cedula, nombre, celular, correo, direccion, rol) VALUES
        {valores}
        ON CONFLICT (usuario) DO UPDATE SET cedula = EXCLUDED.cedula, nombre = EXCLUDED.nombre,
            celular = EXCLUDED.celular, correo = EXCLUDED.correo, direccion = EXCLUDED.direccion,
            rol = EXCLUDED.rol;
    """)
    print(f"DB ACCESO: {len(filas)} clientes registrados")


def variable(nombre):
    return os.environ.get(nombre) or sys.exit(f"Falta la variable {nombre}")


def main():
    if len(sys.argv) != 4 or sys.argv[2] != "--destino" or sys.argv[3] not in ("ad", "acceso", "todo"):
        sys.exit(__doc__)
    destino = sys.argv[3]
    filas = leer_csv(sys.argv[1], COLUMNAS)
    validar(filas)
    if destino in ("ad", "todo"):
        cargar_ad(filas, variable("AD_CONTENEDOR"), variable("AD_CLAVE_ADMIN"))
    if destino in ("acceso", "todo"):
        cargar_db_acceso(filas, variable("DB_ACCESO_CONTENEDOR"))


if __name__ == "__main__":
    main()

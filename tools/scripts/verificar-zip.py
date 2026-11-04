"""Comprueba el .zip de entrega: no lleva secretos ni artefactos de compilación, y sí todo lo que pide el enunciado.

Uso: python3 -I tools/scripts/verificar-zip.py <archivo.zip>
Se ejecuta dentro de un clon de git: lo obligatorio se calcula con `git ls-files`.
"""
import re
import stat
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath

RAIZ = Path(__file__).resolve().parents[2]

CARPETAS_PROHIBIDAS = {"target", "node_modules", "bin", "obj", "dist", ".angular", ".git", ".claude", "secretos"}
EXTENSIONES_PROHIBIDAS = {".pem", ".key", ".crt", ".p12", ".pfx", ".jks", ".apk", ".jar", ".dll", ".log"}
PERMITIDOS = {"services/.mvn/wrapper/maven-wrapper.jar"}
OBLIGATORIOS = ("pom.xml", "*/pom.xml", "*.sln", "*Dockerfile", "*docker-compose*.yml", "*README.md", "*.csproj")
EJECUTABLES = ("services/mvnw",)
CLAVE_PRIVADA = re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----\s*[A-Za-z0-9+/=]{40}")


def sin_prefijo(nombre):
    """SSO-codigo/services/pom.xml → services/pom.xml"""
    return nombre.split("/", 1)[1] if "/" in nombre else nombre


def problemas_de_ruta(ruta):
    camino = PurePosixPath(ruta)
    if ruta in PERMITIDOS:
        return []
    errores = []
    carpetas = set(camino.parts[:-1])
    if carpetas & CARPETAS_PROHIBIDAS:
        errores.append(f"carpeta prohibida: {ruta}")
    if camino.name == ".env" or (camino.name.startswith(".env.") and camino.name != ".env.example"):
        errores.append(f"archivo de entorno: {ruta}")
    if camino.suffix.lower() in EXTENSIONES_PROHIBIDAS:
        errores.append(f"extensión prohibida: {ruta}")
    return errores


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    errores = []
    with zipfile.ZipFile(sys.argv[1]) as archivo:
        entradas = {sin_prefijo(i.filename): i for i in archivo.infolist() if not i.is_dir()}
        for ruta, info in entradas.items():
            errores += problemas_de_ruta(ruta)
            if CLAVE_PRIVADA.search(archivo.read(info)):
                errores.append(f"contiene una clave privada: {ruta}")
        for ruta in EJECUTABLES:
            if ruta in entradas and not (entradas[ruta].external_attr >> 16) & stat.S_IXUSR:
                errores.append(f"perdió el permiso de ejecución: {ruta}")

    versionados = subprocess.run(
        ["git", "ls-files", "--", *OBLIGATORIOS, *EJECUTABLES],
        cwd=RAIZ, check=True, capture_output=True, text=True,
    ).stdout.split()
    errores += [f"falta: {ruta}" for ruta in versionados if ruta not in entradas]

    if errores:
        print("\n".join(f"  {e}" for e in errores), file=sys.stderr)
        sys.exit(f"✗ {sys.argv[1]}: {len(errores)} problemas")
    poms = sum(PurePosixPath(r).name == "pom.xml" for r in entradas)
    print(f"✓ {sys.argv[1]}: {len(entradas)} archivos, {poms} pom.xml, sin secretos ni artefactos de compilación")


main()

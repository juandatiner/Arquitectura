"""Verifica que el código sea idéntico al modelo canónico (docs/modelo/modelo-canonico.json).

Reglas (ver CLAUDE.md):
  R1  Cada componente del diagrama existe con su nombre de artefacto exacto (APIGateway.jar, MSPago.dll…).
  R2  El paquete raíz del componente contiene EXACTAMENTE las clases del diagrama, ni una más ni una menos.
      Solo se permite además la clase de arranque (<Componente>Application / Program).
      El código de soporte vive en los subpaquetes permitidos: modelo, config.
  R3  Cada clase declara todas las operaciones que recibe en los pasos de los casos de uso.
  R4  El puerto configurado es el del nodo del diagrama.

Uso: python3 -I tools/vpp/verificar_codigo.py [--estricto]
  --estricto  también falla si un componente todavía no existe (para la entrega final).
"""
import json
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
MODELO = json.loads((RAIZ / "docs/modelo/modelo-canonico.json").read_text(encoding="utf-8"))
ESTRICTO = "--estricto" in sys.argv

SUBPAQUETES_PERMITIDOS = {"modelo", "config"}
JAVA_BASE = "co/edu/javeriana/sso"

# artefacto del diagrama -> (lenguaje, carpeta del proyecto)
UBICACION = {
    "APIGateway.jar": ("java", "services/apigateway"),
    "MSAcceso.jar": ("java", "services/msacceso"),
    "MSCatalogo.jar": ("java", "services/mscatalogo"),
    "MSSubasta.jar": ("java", "services/mssubasta"),
    "MSSuscripcion.jar": ("java", "services/mssuscripcion"),
    "MSCompra.jar": ("java", "services/mscompra"),
    "MSValidacion.jar": ("java", "services/msvalidacion"),
    "MSDespacho.jar": ("java", "services/msdespacho"),
    "MSNotificacion.jar": ("java", "services/msnotificacion"),
    "OFACBackEnd.jar": ("java", "services/ofacbackend"),
    "SINBackEnd.jar": ("java", "services/sinbackend"),
    "SENBackEnd.jar": ("java", "services/senbackend"),
    "MSPago.dll": ("dotnet", "services-dotnet/MSPago"),
    "YoPagoBackEnd.dll": ("dotnet", "services-dotnet/YoPagoBackEnd"),
    "YoPagoFrontEnd.dll": ("dotnet", "services-dotnet/YoPagoFrontEnd"),
    "SSOWeb.zip": ("angular", "frontend/sso-web"),
    "SSOMobile.apk": ("android", "frontend/sso-mobile"),
}

errores, pendientes = [], []


def error(componente, regla, texto):
    errores.append(f"[{regla}] {componente}: {texto}")


def declara(fuente, op, lenguaje):
    """True si `op` aparece como declaración de método (no como llamada)."""
    if lenguaje == "java":
        patron = rf"(?m)^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public|protected|private|static|final|synchronized|\s)*[\w<>\[\],.? ]+\s+{op}\s*\([^;{{]*\)\s*(?:throws [\w., ]+)?\s*\{{"
    elif lenguaje == "dotnet":
        patron = rf"(?m)^\s*(?:\[[^\]]*\]\s*)*(?:public|protected|private|internal|static|async|virtual|override|\s)*[\w<>\[\],.? ]+\s+{op}\s*\([^;{{]*\)\s*(?:\{{|=>)"
    elif lenguaje == "android":
        patron = rf"(?m)^\s*(?:suspend\s+|private\s+|override\s+|public\s+)*fun\s+{op}\s*\("
    else:  # angular / typescript
        patron = rf"(?m)^\s*(?:public\s+|private\s+|protected\s+|async\s+)*{op}\s*\([^;]*\)\s*(?::\s*[^{{]+)?\{{"
    return re.search(patron, fuente) is not None


def clases_java(carpeta, artefacto):
    nombre = artefacto.removesuffix(".jar").lower()
    raiz = carpeta / "src/main/java" / JAVA_BASE / nombre
    if not raiz.is_dir():
        return None, raiz
    tipos = {f.stem: f for f in raiz.glob("*.java")}
    for sub in raiz.iterdir():
        if sub.is_dir() and sub.name not in SUBPAQUETES_PERMITIDOS:
            error(artefacto, "R2", f"subpaquete no permitido '{sub.name}' (solo {sorted(SUBPAQUETES_PERMITIDOS)})")
    return tipos, raiz


def clases_dotnet(carpeta, artefacto):
    if not carpeta.is_dir():
        return None, carpeta
    tipos = {f.stem: f for f in carpeta.glob("*.cs")}
    for sub in carpeta.iterdir():
        if sub.is_dir() and sub.name not in SUBPAQUETES_PERMITIDOS | {"bin", "obj", "Properties", "Migrations", "Views", "wwwroot"}:
            error(artefacto, "R2", f"carpeta no permitida '{sub.name}'")
    return tipos, carpeta


def clases_por_declaracion(carpeta, extension, patron):
    if not carpeta.is_dir():
        return None, carpeta
    tipos = {}
    for f in carpeta.rglob(f"*{extension}"):
        if any(p in f.parts for p in ("node_modules", "build", "dist", ".angular")):
            continue
        for m in re.finditer(patron, f.read_text(encoding="utf-8", errors="replace")):
            tipos[m.group(1)] = f
    return tipos, carpeta


def puerto_configurado(lenguaje, carpeta):
    if lenguaje == "java":
        f = carpeta / "src/main/resources/application.yml"
        m = re.search(r"(?m)^\s*port:\s*\$\{[A-Z_]+:(\d+)\}|^\s*port:\s*(\d+)", f.read_text()) if f.exists() else None
        return int(m.group(1) or m.group(2)) if m else None
    if lenguaje == "dotnet":
        f = carpeta / "appsettings.json"
        m = re.search(r'"Urls"\s*:\s*"[^"]*:(\d+)"', f.read_text()) if f.exists() else None
        return int(m.group(1)) if m else None
    return None


def main():
    for artefacto, datos in MODELO["componentes"].items():
        if artefacto not in UBICACION:
            error(artefacto, "R1", "componente del diagrama sin ubicación en el repositorio")
            continue
        lenguaje, ruta = UBICACION[artefacto]
        carpeta = RAIZ / ruta
        esperadas = set(datos["clases"])

        if lenguaje == "java":
            tipos, raiz = clases_java(carpeta, artefacto)
        elif lenguaje == "dotnet":
            tipos, raiz = clases_dotnet(carpeta, artefacto)
        elif lenguaje == "angular":
            tipos, raiz = clases_por_declaracion(carpeta / "src", ".ts", r"export\s+class\s+(\w+)")
        else:
            tipos, raiz = clases_por_declaracion(carpeta / "app/src/main", ".kt", r"(?m)^\s*(?:\w+\s+)*class\s+(\w+)")

        if tipos is None:
            (error if ESTRICTO else lambda a, r, t: pendientes.append(a))(artefacto, "R1", f"no existe {raiz.relative_to(RAIZ)}")
            continue

        faltan = esperadas - tipos.keys()
        for c in sorted(faltan):
            error(artefacto, "R2", f"falta la clase {c}")
        if lenguaje in ("java", "dotnet"):
            arranque = {artefacto.split(".")[0] + "Application", "Program", "GlobalUsings"}
            for c in sorted(tipos.keys() - esperadas - arranque):
                error(artefacto, "R2", f"clase {c} no está en el diagrama; muévala a {sorted(SUBPAQUETES_PERMITIDOS)}")

        for c in sorted(esperadas & tipos.keys()):
            fuente = tipos[c].read_text(encoding="utf-8", errors="replace")
            for op in MODELO["operaciones"].get(c, []):
                if not declara(fuente, op, lenguaje):
                    error(artefacto, "R3", f"{c} no declara la operación {op}(…)")

        puertos = {n["puerto"] for n in datos["nodos"] if n["puerto"]}
        if puertos and lenguaje in ("java", "dotnet"):
            p = puerto_configurado(lenguaje, carpeta)
            if p not in puertos:
                error(artefacto, "R4", f"puerto configurado {p}, el diagrama dice {sorted(puertos)}")

    if pendientes:
        print(f"Pendientes (aún sin código): {', '.join(pendientes)}")
    if errores:
        print("\n".join(errores))
        print(f"\n✗ {len(errores)} diferencias con el diagrama Arquitectura Integrada JavaEE-.NET")
        sys.exit(1)
    print("✓ El código coincide con el diagrama Arquitectura Integrada JavaEE-.NET")


if __name__ == "__main__":
    main()

"""SMS SERVER :9004 (nodo del diagrama, estereotipo HTTP · Máquina 3): pasarela de SMS.

Recibe los SMS de IntegraSMS (paso IX.7) y los guarda en /datos/sms.jsonl, como Mailpit guarda los
correos. Para usar un proveedor real de SMS basta con apuntar RUTA_SMS de MSNotificacion a él.

  POST /sms   {"numero": "...", "texto": "..."}  →  202 {"id": "..."}
  GET  /sms   lista de SMS recibidos (los más recientes primero)
  GET  /salud estado

Modo de falla, para probar reintentos (apagado por defecto). Solo afecta a los POST /sms válidos:

  latencia_ms  espera antes de responder (0 = sin espera)
  errores_503  cuántos POST /sms siguientes responden 503 sin guardar (0 = ninguno, -1 = todos)

Se fija al arrancar con SMS_FALLA_LATENCIA_MS y SMS_FALLA_ERRORES_503, y en caliente con:

  GET /control/falla   estado actual
  PUT /control/falla   {"latencia_ms": 6000, "errores_503": 2}; el campo omitido queda en 0
"""
import json
import os
import re
import threading
import time
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ARCHIVO = Path("/datos/sms.jsonl")
CANDADO = threading.Lock()
FALLA = {"latencia_ms": 0, "errores_503": 0}


def validar_falla(latencia_ms=0, errores_503=0):
    """Devuelve el modo de falla o lanza ValueError si los valores no son enteros en rango."""
    for valor in (latencia_ms, errores_503):
        if type(valor) is not int:
            raise ValueError("latencia_ms y errores_503 deben ser enteros")
    if latencia_ms < 0 or errores_503 < -1:
        raise ValueError("se esperaba latencia_ms >= 0 y errores_503 >= -1")
    return {"latencia_ms": latencia_ms, "errores_503": errores_503}


def falla_del_entorno(entorno):
    """Lee el modo de falla inicial; un valor inválido impide arrancar en vez de ignorarse."""
    return validar_falla(int(entorno.get("SMS_FALLA_LATENCIA_MS") or 0),
                         int(entorno.get("SMS_FALLA_ERRORES_503") or 0))


def aplicar_falla():
    """Aplica la latencia y dice si este envío debe responder 503, consumiendo uno de los errores."""
    with CANDADO:
        latencia_ms, errores = FALLA["latencia_ms"], FALLA["errores_503"]
        if errores > 0:
            FALLA["errores_503"] = errores - 1
    time.sleep(latencia_ms / 1000)
    return errores != 0


class Pasarela(BaseHTTPRequestHandler):
    def _responder(self, estado, cuerpo):
        datos = json.dumps(cuerpo, ensure_ascii=False).encode()
        self.send_response(estado)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def _leer_cuerpo(self):
        """Lee el cuerpo con Content-Length o con Transfer-Encoding: chunked (HttpClient del JDK)."""
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            partes = []
            while True:
                tamano = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if tamano == 0:
                    self.rfile.readline()
                    return b"".join(partes)
                partes.append(self.rfile.read(tamano))
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_POST(self):
        if self.path != "/sms":
            return self._responder(404, {"detalle": "no existe"})
        try:
            sms = json.loads(self._leer_cuerpo())
            numero, texto = str(sms["numero"]), str(sms["texto"])
        except (ValueError, KeyError, TypeError):
            return self._responder(400, {"detalle": "se esperaba {numero, texto}"})
        if not re.fullmatch(r"\+?\d{7,15}", numero) or not texto:
            return self._responder(400, {"detalle": "número o texto inválido"})
        if aplicar_falla():
            return self._responder(503, {"detalle": "falla simulada"})
        registro = {"id": str(uuid.uuid4()), "numero": numero, "texto": texto,
                    "recibido": datetime.now(timezone.utc).isoformat()}
        with CANDADO, ARCHIVO.open("a", encoding="utf-8") as archivo:
            archivo.write(json.dumps(registro, ensure_ascii=False) + "\n")
        self._responder(202, {"id": registro["id"]})

    def do_GET(self):
        if self.path == "/salud":
            return self._responder(200, {"estado": "UP"})
        if self.path == "/control/falla":
            with CANDADO:
                actual = dict(FALLA)
            return self._responder(200, actual)
        if self.path != "/sms":
            return self._responder(404, {"detalle": "no existe"})
        with CANDADO:
            lineas = ARCHIVO.read_text(encoding="utf-8").splitlines() if ARCHIVO.exists() else []
        self._responder(200, [json.loads(l) for l in reversed(lineas)])

    def do_PUT(self):
        if self.path != "/control/falla":
            return self._responder(404, {"detalle": "no existe"})
        try:
            nueva = validar_falla(**json.loads(self._leer_cuerpo()))
        except (ValueError, TypeError) as error:
            return self._responder(400, {"detalle": f"se esperaba {{latencia_ms, errores_503}}: {error}"})
        with CANDADO:
            FALLA.update(nueva)
        print("SMS SERVER: modo de falla", nueva, flush=True)
        self._responder(200, nueva)

    def log_message(self, formato, *args):
        print("SMS SERVER:", formato % args, flush=True)


if __name__ == "__main__":
    FALLA.update(falla_del_entorno(os.environ))
    print("SMS SERVER: modo de falla", FALLA, flush=True)
    ARCHIVO.parent.mkdir(parents=True, exist_ok=True)
    ThreadingHTTPServer(("0.0.0.0", 9004), Pasarela).serve_forever()

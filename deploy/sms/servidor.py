"""SMS SERVER :9004 (nodo del diagrama, estereotipo HTTP · Máquina 3): pasarela de SMS.

Recibe los SMS de IntegraSMS (paso IX.7) y los guarda en /datos/sms.jsonl, como Mailpit guarda los
correos. Para usar un proveedor real de SMS basta con apuntar RUTA_SMS de MSNotificacion a él.

  POST /sms   {"numero": "...", "texto": "..."}  →  202 {"id": "..."}
  GET  /sms   lista de SMS recibidos (los más recientes primero)
  GET  /salud estado
"""
import json
import re
import threading
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ARCHIVO = Path("/datos/sms.jsonl")
CANDADO = threading.Lock()


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
        except (ValueError, KeyError):
            return self._responder(400, {"detalle": "se esperaba {numero, texto}"})
        if not re.fullmatch(r"\+?\d{7,15}", numero) or not texto:
            return self._responder(400, {"detalle": "número o texto inválido"})
        registro = {"id": str(uuid.uuid4()), "numero": numero, "texto": texto,
                    "recibido": datetime.now(timezone.utc).isoformat()}
        with CANDADO, ARCHIVO.open("a", encoding="utf-8") as archivo:
            archivo.write(json.dumps(registro, ensure_ascii=False) + "\n")
        self._responder(202, {"id": registro["id"]})

    def do_GET(self):
        if self.path == "/salud":
            return self._responder(200, {"estado": "UP"})
        if self.path != "/sms":
            return self._responder(404, {"detalle": "no existe"})
        with CANDADO:
            lineas = ARCHIVO.read_text(encoding="utf-8").splitlines() if ARCHIVO.exists() else []
        self._responder(200, [json.loads(l) for l in reversed(lineas)])

    def log_message(self, formato, *args):
        print("SMS SERVER:", formato % args, flush=True)


if __name__ == "__main__":
    ARCHIVO.parent.mkdir(parents=True, exist_ok=True)
    ThreadingHTTPServer(("0.0.0.0", 9004), Pasarela).serve_forever()

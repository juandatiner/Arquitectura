#!/usr/bin/env python3
"""Devuelve a su cola los mensajes que agotaron sus reintentos y quedaron en <cola>.dlq.

Uso (en Máquina 2, con la API de gestión de RabbitMQ):
  MOM_USUARIO=... MOM_CLAVE=... python3 -I tools/scripts/reprocesar-dlq.py q.notificacion [cantidad] [url-gestion]

Los consumidores son idempotentes: reprocesar no duplica efectos (por ejemplo, MSNotificacion no
repite un correo ya enviado).
"""
import base64
import json
import os
import sys
import urllib.parse
import urllib.request

if len(sys.argv) < 2:
    sys.exit(__doc__)
cola = sys.argv[1]
cantidad = int(sys.argv[2]) if len(sys.argv) > 2 else 100
gestion = sys.argv[3] if len(sys.argv) > 3 else "http://localhost:15672"
usuario = os.environ.get("MOM_USUARIO") or sys.exit("Falta MOM_USUARIO")
clave = os.environ.get("MOM_CLAVE") or sys.exit("Falta MOM_CLAVE")
autorizacion = "Basic " + base64.b64encode(f"{usuario}:{clave}".encode()).decode()


def api(ruta, cuerpo):
    peticion = urllib.request.Request(
        f"{gestion}/api/{ruta}", data=json.dumps(cuerpo).encode(),
        headers={"Authorization": autorizacion, "Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(peticion))


dlq = urllib.parse.quote(f"{cola}.dlq", safe="")
reenviados = 0
for _ in range(cantidad):
    mensajes = api(f"queues/%2F/{dlq}/get", {"count": 1, "ackmode": "ack_requeue_false", "encoding": "auto"})
    if not mensajes:
        break
    m = mensajes[0]
    propiedades = {k: m["properties"][k] for k in ("type", "message_id", "content_type") if k in m["properties"]}
    api(f"exchanges/%2F/{urllib.parse.quote(cola, safe='')}/publish",
        {"properties": propiedades, "routing_key": "", "payload": m["payload"], "payload_encoding": m["payload_encoding"]})
    reenviados += 1
print(f"{reenviados} mensajes devueltos de {cola}.dlq a {cola}")

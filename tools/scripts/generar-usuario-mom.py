#!/usr/bin/env python3
"""Genera deploy/secretos/mom/usuarios.json: usuario de RabbitMQ (MOM SERVER) con su clave cifrada.

Uso: python3 -I tools/scripts/generar-usuario-mom.py <usuario> <clave> [carpeta-destino]
"""
import base64
import hashlib
import json
import os
import sys
from pathlib import Path

usuario, clave = sys.argv[1], sys.argv[2]
destino = Path(sys.argv[3] if len(sys.argv) > 3 else "deploy/secretos/mom")
sal = os.urandom(4)
hash_clave = base64.b64encode(sal + hashlib.sha256(sal + clave.encode()).digest()).decode()
destino.mkdir(parents=True, exist_ok=True)
(destino / "usuarios.json").write_text(json.dumps({
    "users": [{"name": usuario, "password_hash": hash_clave,
               "hashing_algorithm": "rabbit_password_hashing_sha256", "tags": ["administrator"]}],
    "permissions": [{"user": usuario, "vhost": "/", "configure": ".*", "write": ".*", "read": ".*"}],
}, indent=2))
os.chmod(destino / "usuarios.json", 0o644)
print(f"{destino}/usuarios.json listo (no se sube al repositorio)")

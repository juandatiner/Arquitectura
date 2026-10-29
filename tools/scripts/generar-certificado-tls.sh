#!/usr/bin/env bash
# Certificado TLS autofirmado del balanceador (HTTPS de VII.20, IntegraSSO → Nginx).
# Uso: tools/scripts/generar-certificado-tls.sh <carpeta-destino> [IP-maquina-1]
set -euo pipefail
destino="${1:-deploy/secretos}"
ip="${2:-10.43.99.2}"
mkdir -p "$destino"
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout "$destino/balanceador.key" -out "$destino/balanceador.crt" \
  -subj "/CN=$ip/O=SSO Javeriana" \
  -addext "subjectAltName=IP:$ip,DNS:localhost,DNS:balanceador" 2>/dev/null
chmod 644 "$destino/balanceador.key" "$destino/balanceador.crt"
echo "Certificado en $destino (no se sube al repositorio)"

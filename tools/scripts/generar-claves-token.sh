#!/usr/bin/env bash
# Genera el par de claves RSA del token (RS256).
#   token_privada.pem -> MSAcceso (firma)     · Máquina 2
#   token_publica.pem -> APIGateway (valida)  · Máquina 1
# Uso: tools/scripts/generar-claves-token.sh <carpeta-destino>
set -euo pipefail
destino="${1:-deploy/secretos}"
mkdir -p "$destino"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$destino/token_privada.pem"
openssl pkey -in "$destino/token_privada.pem" -pubout -out "$destino/token_publica.pem"
chmod 644 "$destino/token_privada.pem" "$destino/token_publica.pem"
echo "Claves en $destino (no se suben al repositorio)"

#!/usr/bin/env bash
# Construye las imágenes linux/amd64 de los componentes del diagrama con los Dockerfile del repositorio.
#   imagenes.sh push [componente]   publica en IMAGE_REGISTRY
#   imagenes.sh load [componente]   las deja en el Docker local, sin publicar
#   imagenes.sh lista               imprime el nombre de cada componente
# Variables: IMAGE_REGISTRY e IMAGE_TAG (obligatorias). SOLO_MOSTRAR=1 imprime los comandos sin ejecutarlos.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# módulo:artefacto:puerto del nodo en el diagrama
JAVA=(
  apigateway:APIGateway:8080
  msacceso:MSAcceso:8081
  mscatalogo:MSCatalogo:8082
  mssubasta:MSSubasta:8083
  mssuscripcion:MSSuscripcion:8084
  mscompra:MSCompra:8085
  msvalidacion:MSValidacion:8087
  msdespacho:MSDespacho:8088
  msnotificacion:MSNotificacion:8089
  ofacbackend:OFACBackEnd:9001
  sinbackend:SINBackEnd:9002
  senbackend:SENBackEnd:9003
)
# proyecto:puerto del nodo en el diagrama
DOTNET=(
  MSPago:5086
  YoPagoBackEnd:5001
  YoPagoFrontEnd:5000
)

minuscula() { tr '[:upper:]' '[:lower:]' <<<"$1"; }

todos() {
  local entrada
  for entrada in "${JAVA[@]}"; do echo "${entrada%%:*}"; done
  for entrada in "${DOTNET[@]}"; do minuscula "${entrada%%:*}"; done
  echo ssoweb
}

MODO="${1:-}"
FILTRO="${2:-}"

case "$MODO" in
  lista) todos; exit 0 ;;
  push) SALIDA=--push ;;
  load) SALIDA=--load ;;
  *) echo "Uso: $0 push|load|lista [componente]" >&2; exit 2 ;;
esac

: "${IMAGE_REGISTRY:?Falta IMAGE_REGISTRY (el de deploy/maquina-N/.env)}"
: "${IMAGE_TAG:?Falta IMAGE_TAG (el de deploy/maquina-N/.env)}"

# Sin grep -q: cerraría el pipe antes de tiempo y pipefail lo tomaría como fallo.
if [ -n "$FILTRO" ] && ! todos | grep -x "$FILTRO" >/dev/null; then
  echo "Componente desconocido: $FILTRO. Disponibles: $(todos | tr '\n' ' ')" >&2
  exit 2
fi

construir() { # nombre, dockerfile, contexto, argumentos de construcción...
  local nombre="$1" dockerfile="$2" contexto="$3"
  shift 3
  [ -z "$FILTRO" ] || [ "$FILTRO" = "$nombre" ] || return 0
  local comando=(docker buildx build --platform linux/amd64 "$SALIDA" -f "$dockerfile" "$@"
    -t "$IMAGE_REGISTRY/sso-$nombre:$IMAGE_TAG" "$contexto")
  echo "+ ${comando[*]}"
  [ -n "${SOLO_MOSTRAR:-}" ] || "${comando[@]}"
}

cd "$RAIZ"
for entrada in "${JAVA[@]}"; do
  IFS=: read -r modulo artefacto puerto <<<"$entrada"
  construir "$modulo" services/Dockerfile services \
    --build-arg "MODULO=$modulo" --build-arg "ARTEFACTO=$artefacto" --build-arg "PUERTO=$puerto"
done
for entrada in "${DOTNET[@]}"; do
  IFS=: read -r proyecto puerto <<<"$entrada"
  construir "$(minuscula "$proyecto")" services-dotnet/Dockerfile services-dotnet \
    --build-arg "PROYECTO=$proyecto" --build-arg "PUERTO=$puerto"
done
construir ssoweb frontend/sso-web/Dockerfile frontend/sso-web

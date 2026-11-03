#!/usr/bin/env bash
# Genera dist/images-maquina-N.tar con las imágenes que usa el compose de cada máquina, para copiarlas
# por scp cuando no hay registro. No publica nada.
# Variables: IMAGE_REGISTRY e IMAGE_TAG (obligatorias), DIST (por defecto dist), SIN_CONSTRUIR=1 para
# reutilizar imágenes ya construidas con `make images-local`.
# Ojo en un Mac arm64: `docker pull --platform linux/amd64` de postgres, nginx, etc. deja esa variante
# bajo el mismo nombre local; el entorno de desarrollo la usaría emulada hasta volver a hacer pull.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RAIZ"
DIST="${DIST:-dist}"

: "${IMAGE_REGISTRY:?Falta IMAGE_REGISTRY (el de deploy/maquina-N/.env)}"
: "${IMAGE_TAG:?Falta IMAGE_TAG (el de deploy/maquina-N/.env)}"
export IMAGE_REGISTRY IMAGE_TAG

# Primero se comprueba todo, para no construir durante minutos y fallar al final.
for n in 1 2 3; do
  for archivo in "deploy/maquina-$n/docker-compose.yml" "deploy/maquina-$n/.env.example"; do
    if [ ! -f "$archivo" ]; then
      echo "Falta $archivo: el despliegue por máquina todavía no existe en esta rama. No se exportó nada." >&2
      exit 1
    fi
  done
done

imagenes_de() { # máquina
  docker compose -f "deploy/maquina-$1/docker-compose.yml" --env-file "deploy/maquina-$1/.env.example" config --images | sort -u
}
for n in 1 2 3; do
  imagenes_de "$n" >/dev/null
done

[ -n "${SIN_CONSTRUIR:-}" ] || tools/scripts/imagenes.sh load

mkdir -p "$DIST"
for n in 1 2 3; do
  imagenes=()
  while IFS= read -r imagen; do
    imagenes+=("$imagen")
    case "$imagen" in
      "$IMAGE_REGISTRY"/sso-*)
        docker image inspect "$imagen" >/dev/null 2>&1 || {
          echo "Falta la imagen local $imagen: ejecutar make images-local" >&2
          exit 1
        }
        ;;
      *) docker pull -q --platform linux/amd64 "$imagen" >/dev/null ;;
    esac
  done < <(imagenes_de "$n")
  echo "+ $DIST/images-maquina-$n.tar: ${imagenes[*]}"
  docker save -o "$DIST/images-maquina-$n.tar" "${imagenes[@]}"
done

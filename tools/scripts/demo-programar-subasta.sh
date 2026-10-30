#!/usr/bin/env bash
# Solo para la demo / sustentación: reprograma las fechas de una subasta en DB CATALOGO y DB SUBASTA
# (las dos bases de Máquina 2) para no esperar días a que abra y cierre.
#   tools/scripts/demo-programar-subasta.sh SUB-0001 0 3   → abre ya y cierra en 3 minutos
# Contenedores configurables: DB_CATALOGO_CONTENEDOR, DB_SUBASTA_CONTENEDOR.
set -euo pipefail
id="${1:?idSubasta}"; inicio="${2:-0}"; duracion="${3:-3}"
cat_c="${DB_CATALOGO_CONTENEDOR:-sso-dev-db-catalogo-1}"
sub_c="${DB_SUBASTA_CONTENEDOR:-sso-dev-db-subasta-1}"
fechas="fecha_inicio = now() + interval '$inicio minutes' - interval '5 seconds', fecha_cierre = now() + interval '$((inicio + duracion)) minutes'"
docker exec "$cat_c" psql -U catalogo -d catalogo -qc "UPDATE subasta SET $fechas WHERE id_subasta = '$id'"
docker exec "$sub_c" psql -U subasta -d subasta -qc "UPDATE subasta SET $fechas WHERE id_subasta = '$id'"
echo "$id: abre en $inicio min y cierra $duracion min después"

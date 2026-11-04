#!/usr/bin/env bash
# Comprueba desde una máquina los puertos de las otras, según la tabla de firewall de deploy/README.md.
# Uso: MAQUINA2_IP=<ip> MAQUINA3_IP=<ip> tools/scripts/check-red.sh <1|2|3>
#   Máquina 1 necesita MAQUINA2_IP y MAQUINA3_IP · Máquina 2, MAQUINA3_IP · Máquina 3, MAQUINA1_IP y MAQUINA2_IP
#   CHECK_RED_TIMEOUT: segundos de espera por puerto (3 si no se indica)
# El stack de la máquina destino debe estar arriba: un puerto sin servicio falla igual que uno cerrado.
# Código de salida: 0 todo alcanzable · 1 algún puerto falló · 2 uso incorrecto o falta configuración.
set -euo pipefail

uso() {
  echo "Uso: MAQUINA<N>_IP=<ip> $0 <1|2|3>" >&2
  exit 2
}

[ "$#" -eq 1 ] || uso
origen="$1"

# Destino, puerto y quién lo usa
case "$origen" in
  1)
    comprobaciones=(
      "2 8081 MSAcceso, desde SrvRuteo"
      "2 8082 MSCatalogo, desde SrvRuteo"
      "2 8083 MSSubasta, desde SrvRuteo"
      "2 8084 MSSuscripcion, desde SrvRuteo"
      "2 8085 MSCompra replica 1, desde Nginx"
      "3 8085 MSCompra replica 2, desde Nginx"
      "3 5001 YoPagoBackEnd, desde ProxyWSYoPagoWeb"
      "3 5086 MSPago, desde SrvRuteo"
    )
    ;;
  2)
    comprobaciones=(
      "3 389 AD, desde MSAcceso"
      "3 9001 OFACBackEnd, desde IntegraOFAC"
      "3 1025 MAIL, desde IntegraMail"
      "3 9004 SMS, desde IntegraSMS"
    )
    ;;
  3)
    comprobaciones=(
      "1 443 Nginx, desde IntegraSSO"
      "2 8087 MSValidacion, desde MSCompra replica 2"
      "2 5432 DB COMPRA, desde MSCompra replica 2"
      "2 5672 MOM, desde MSCompra replica 2, MSPago, SIN y SEN"
    )
    ;;
  *)
    uso
    ;;
esac

if ! command -v nc > /dev/null 2>&1; then
  echo "Falta el comando nc (en Ubuntu: sudo apt install netcat-openbsd)" >&2
  exit 2
fi

espera="${CHECK_RED_TIMEOUT:-3}"
case "$espera" in
  '' | *[!0-9]* | 0)
    echo "CHECK_RED_TIMEOUT debe ser un entero positivo de segundos" >&2
    exit 2
    ;;
esac

faltan=""
for comprobacion in "${comprobaciones[@]}"; do
  variable="MAQUINA${comprobacion%% *}_IP"
  if [ -z "${!variable:-}" ]; then
    case " $faltan " in
      *" $variable "*) ;;
      *) faltan="${faltan:+$faltan }$variable" ;;
    esac
  fi
done
if [ -n "$faltan" ]; then
  echo "Faltan variables de entorno: $faltan" >&2
  exit 2
fi

# No todas las variantes de nc acotan la conexión con -w: timeout garantiza el límite por puerto
limite=()
if command -v timeout > /dev/null 2>&1; then
  limite=(timeout "$espera")
fi

echo "Conectividad desde Máquina $origen (timeout ${espera}s)"
fallos=0
for comprobacion in "${comprobaciones[@]}"; do
  read -r destino puerto servicio <<< "$comprobacion"
  variable="MAQUINA${destino}_IP"
  ip="${!variable}"
  if salida="$(${limite[@]+"${limite[@]}"} nc -zv -w "$espera" "$ip" "$puerto" 2>&1)"; then
    printf '  OK     Máquina %s %s:%s  %s\n' "$destino" "$ip" "$puerto" "$servicio"
  else
    fallos=$((fallos + 1))
    printf '  FALLA  Máquina %s %s:%s  %s\n' "$destino" "$ip" "$puerto" "$servicio"
    printf '         %s\n' "${salida:-sin respuesta en ${espera}s}"
  fi
done

total="${#comprobaciones[@]}"
if [ "$fallos" -gt 0 ]; then
  echo "Resultado: $fallos de $total puertos fallaron" >&2
  exit 1
fi
echo "Resultado: $total de $total puertos alcanzables"

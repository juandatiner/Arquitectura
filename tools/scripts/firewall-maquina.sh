#!/usr/bin/env bash
# Aplica la tabla de firewall de deploy/README.md a los puertos que publica Docker: ufw no los filtra,
# porque Docker los enruta por FORWARD, así que las reglas van en la cadena DOCKER-USER.
# Solo filtra conexiones nuevas que entran por la interfaz externa; no toca SSH ni el tráfico entre contenedores.
# Instala sso-firewall.service para reaplicarlas tras cada reinicio. Deshacer: tools/scripts/firewall-maquina.sh quitar
#
# Uso: sudo MAQUINA1_IP=<ip> MAQUINA2_IP=<ip> MAQUINA3_IP=<ip> tools/scripts/firewall-maquina.sh <2|3|quitar>
# Máquina 1 no lleva reglas: es el perímetro y sus puertos 80, 443 y 5000 son la entrada al sistema.
set -euo pipefail
CADENA=SSO-FIREWALL
UNIDAD=/etc/systemd/system/sso-firewall.service

vaciar() {
    iptables -D DOCKER-USER -j "$CADENA" 2>/dev/null || true
    iptables -F "$CADENA" 2>/dev/null || true
    iptables -X "$CADENA" 2>/dev/null || true
}
permitir() { # permitir <ip de origen> <puerto...>
    local origen=$1 puerto
    shift
    for puerto in "$@"; do
        iptables -A "$CADENA" -i "$INTERFAZ" -p tcp -s "$origen" -m conntrack --ctstate NEW --ctorigdstport "$puerto" -j RETURN
        iptables -A "$CADENA" -i "$INTERFAZ" -p tcp -m conntrack --ctstate NEW --ctorigdstport "$puerto" -j DROP
    done
}

maquina=${1:-}
if [ "$maquina" = quitar ]; then
    vaciar
    systemctl disable --now sso-firewall.service 2>/dev/null || true
    rm -f "$UNIDAD"
    systemctl daemon-reload
    exit 0
fi
: "${MAQUINA1_IP:?Falta MAQUINA1_IP}" "${MAQUINA2_IP:?Falta MAQUINA2_IP}" "${MAQUINA3_IP:?Falta MAQUINA3_IP}"
INTERFAZ=$(ip -4 route show default | awk "{print \$5; exit}")
vaciar
iptables -N "$CADENA"
case "$maquina" in
    2)
        permitir "$MAQUINA1_IP" 8081 8082 8083 8084 8085
        permitir "$MAQUINA3_IP" 8087 5432 5672
        ;;
    3)
        permitir "$MAQUINA1_IP" 8085 5001 5086
        permitir "$MAQUINA2_IP" 389 9001 1025 9004
        ;;
    *)
        echo "Uso: $0 <2|3|quitar>" >&2
        exit 2
        ;;
esac
iptables -I DOCKER-USER 1 -j "$CADENA"
cat >"$UNIDAD" <<U
[Unit]
Description=Firewall SSO de los puertos publicados por Docker
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
Environment=MAQUINA1_IP=$MAQUINA1_IP MAQUINA2_IP=$MAQUINA2_IP MAQUINA3_IP=$MAQUINA3_IP
ExecStart=$(readlink -f "$0") $maquina

[Install]
WantedBy=multi-user.target
U
systemctl daemon-reload
systemctl enable sso-firewall.service >/dev/null 2>&1
echo "Firewall de Máquina $maquina aplicado en $INTERFAZ"

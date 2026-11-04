# Aporte grupal: microservicios en distintas máquinas comunicados por Docker

Cada máquina corre su propio motor Docker con su `deploy/maquina-N/docker-compose.yml`. Los contenedores de máquinas distintas
se comunican por los puertos publicados en la IP de la otra máquina.

| Origen | Destino | Protocolo | Configuración |
|--------|---------|-----------|---------------|
| `MSCompra` réplica 1 (Máquina 2) | `MSValidacion` (Máquina 2) | HTTP/REST | `RUTA_MSVALIDACION=http://msvalidacion:8087` |
| `MSValidacion` (Máquina 2) | `OFACBackEnd` (Máquina 3) | HTTP/REST | `RUTA_OFAC=http://${MAQUINA3_IP}:9001` |
| `MSCompra` réplica 2 (Máquina 3) | RabbitMQ (Máquina 2) | AMQP | `MOM_HOST=${MAQUINA2_IP}`, puerto 5672 |

## Evidencia

Salida real de las tres VM (2026-11-04), tras la prueba de extremo a extremo. En la lista de conexiones, `10.43.99.45` es Máquina 3: la réplica 2 de `MSCompra`, `MSPago`, SIN y SEN contra el MOM de Máquina 2.

### Máquina 1 · `docker ps` (10.43.99.2)

```text
NAMES                            STATUS
apigateway-1       Up 10 minutes (healthy)
ssoweb-1           Up 2 hours (healthy)
yopagofrontend-1   Up 2 hours (healthy)
balanceador-1      Up 2 hours (healthy)
```

### Máquina 2 · `docker ps` (10.43.100.238)

```text
NAMES                               STATUS
mscatalogo-1          Up 2 minutes (healthy)
mscompra-replica1-1   Up 7 minutes (healthy)
msacceso-1            Up 2 hours (healthy)
msdespacho-1          Up 2 hours (healthy)
msnotificacion-1      Up 2 hours (healthy)
msvalidacion-1        Up 2 hours (healthy)
mssuscripcion-1       Up 20 minutes (healthy)
mssubasta-1           Up 20 minutes (healthy)
db-notificacion-1     Up 2 hours (healthy)
db-compra-1           Up 2 hours (healthy)
db-validar-1          Up 2 hours (healthy)
db-acceso-1           Up 2 hours (healthy)
mom-1                 Up 15 minutes (healthy)
db-subasta-1          Up 2 hours (healthy)
db-despacho-1         Up 2 hours (healthy)
db-suscripcion-1      Up 2 hours (healthy)
db-catalogo-1         Up 2 hours (healthy)
```

### Máquina 3 · `docker ps` (10.43.99.45)

```text
NAMES                               STATUS
mscompra-replica2-1   Up 23 minutes (healthy)
yopagobackend-1       Up 12 minutes (healthy)
mspago-1              Up 2 hours (healthy)
ofacbackend-1         Up 18 minutes (healthy)
senbackend-1          Up 2 hours (healthy)
sinbackend-1          Up 2 hours (healthy)
db-sen-1              Up 2 hours (healthy)
db-pago-1             Up 2 hours (healthy)
db-yopago-1           Up 2 hours (healthy)
db-ofac-1             Up 2 hours (healthy)
db-sin-1              Up 2 hours (healthy)
sms-1                 Up 2 hours (healthy)
mail-1                Up 2 hours (healthy)
ad-1                  Up 2 hours (healthy)
```

### Comunicación entre máquinas

`rabbitmqctl list_connections peer_host user state` en Máquina 2, y resultado de `tools/scripts/e2e.sh` y `tools/scripts/escenarios-falla.sh`:

```text
4 10.43.99.45	sso_mom	running
      1 172.23.0.10	sso_mom	running
      1 172.23.0.3	sso_mom	running
      1 172.23.0.4	sso_mom	running
      1 172.23.0.5	sso_mom	running
      1 172.23.0.6	sso_mom	running
      1 172.23.0.7	sso_mom	running
      1 peer_host	user	state
== Resultado: 85 de 85 comprobaciones bien, 0 fallos, 66s ==
  OK    [ 59s] compra TERMINADA en 59s (criterio: menos de 2 min)
== Resultado: 123 de 123 comprobaciones bien, 0 fallos, 720s ==
```

`tools/scripts/check-red.sh` alcanzó 16 de 16 puertos entre máquinas con el firewall activo. Desde fuera de ellas, los puertos de Máquinas 2 y 3 quedan bloqueados (`tools/scripts/firewall-maquina.sh`).

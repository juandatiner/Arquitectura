# Aporte grupal: microservicios en distintas máquinas comunicados por Docker

Cada máquina corre su propio motor Docker con su `deploy/maquina-N/docker-compose.yml`. Los contenedores de máquinas distintas
se comunican por los puertos publicados en la IP de la otra máquina.

| Origen | Destino | Protocolo | Configuración |
|--------|---------|-----------|---------------|
| `MSCompra` réplica 1 (Máquina 2) | `MSValidacion` (Máquina 2) | HTTP/REST | `RUTA_MSVALIDACION=http://msvalidacion:8087` |
| `MSValidacion` (Máquina 2) | `OFACBackEnd` (Máquina 3) | HTTP/REST | `RUTA_OFAC=http://${MAQUINA3_IP}:9001` |
| `MSCompra` réplica 2 (Máquina 3) | RabbitMQ (Máquina 2) | AMQP | `MOM_HOST=${MAQUINA2_IP}`, puerto 5672 |

## Evidencia

> **Pendiente.** Aún no se ha desplegado en las VM: esta sección se llena con salidas y capturas reales.

Después de desplegar (`deploy/README.md`) y de hacer una compra que pase por la validación OFAC:

```bash
# En cada máquina, desde ~/sso/deploy/maquina-N
docker ps

# Máquina 3: la réplica 2 conectada al MOM de Máquina 2
docker compose logs mscompra-replica2 | grep -i "rabbit\|amqp"

# Máquina 2: conexiones AMQP abiertas, con la IP de Máquina 3 como origen
docker compose exec mom rabbitmqctl list_connections name peer_host user state

# Máquina 2: la validación llamando a OFAC
docker compose logs msvalidacion

# Máquina 3: OFAC atendiendo la consulta
docker compose logs ofacbackend
```

### Máquina 1 · `docker ps`

Pendiente.

### Máquina 2 · `docker ps`

Pendiente.

### Máquina 3 · `docker ps`

Pendiente.

### Logs de la comunicación

Pendiente.

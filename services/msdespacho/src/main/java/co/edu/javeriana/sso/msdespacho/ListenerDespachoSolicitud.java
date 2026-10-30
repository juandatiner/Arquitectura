package co.edu.javeriana.sso.msdespacho;

import co.edu.javeriana.sso.msdespacho.modelo.DespachoSolicitud;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.despachoSolicitud. */
@Component
public class ListenerDespachoSolicitud {

    private final SrvDespacho srvDespacho;
    private final JsonMapper json;

    public ListenerDespachoSolicitud(SrvDespacho srvDespacho, JsonMapper json) {
        this.srvDespacho = srvDespacho;
        this.json = json;
    }

    /**
     * Pasos VIII.3 (q.despachoSolicitud → entregar(evento)) y VIII.4 (→ SrvDespacho
     * solicitarDespacho).
     */
    @RabbitListener(queues = "q.despachoSolicitud")
    public void entregar(String evento) {
        DespachoSolicitud solicitud = json.readValue(evento, DespachoSolicitud.class);
        srvDespacho.solicitarDespacho(
                solicitud.numeroCompra(), solicitud.idBien(), solicitud.direccion());
    }
}

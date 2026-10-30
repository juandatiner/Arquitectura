package co.edu.javeriana.sso.sinbackend;

import co.edu.javeriana.sso.sinbackend.modelo.SolicitudInventario;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.inventarioSolicitud. */
@Component
public class ListenerInventario {

    private final SrvInventario srvInventario;
    private final JsonMapper json;

    public ListenerInventario(SrvInventario srvInventario, JsonMapper json) {
        this.srvInventario = srvInventario;
        this.json = json;
    }

    /** Pasos VIII.9a (q.inventarioSolicitud → entregar(evento)) y VIII.10a (→ SrvInventario). */
    @RabbitListener(queues = "q.inventarioSolicitud")
    public void entregar(String evento) {
        SolicitudInventario solicitud = json.readValue(evento, SolicitudInventario.class);
        srvInventario.registrarSalidaInventario(solicitud.numeroCompra(), solicitud.idBien());
    }
}

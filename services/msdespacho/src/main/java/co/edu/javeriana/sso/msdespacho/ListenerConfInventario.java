package co.edu.javeriana.sso.msdespacho;

import co.edu.javeriana.sso.msdespacho.modelo.Confirmacion;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.inventarioConfirmacion. */
@Component
public class ListenerConfInventario {

    private final SrvDespacho srvDespacho;
    private final JsonMapper json;

    public ListenerConfInventario(SrvDespacho srvDespacho, JsonMapper json) {
        this.srvDespacho = srvDespacho;
        this.json = json;
    }

    /**
     * Pasos VIII.15a (q.inventarioConfirmacion → entregar(evento)) y VIII.16a (→ SrvDespacho
     * confirmarInventario).
     */
    @RabbitListener(queues = "q.inventarioConfirmacion")
    public void entregar(String evento) {
        srvDespacho.confirmarInventario(json.readValue(evento, Confirmacion.class).numeroCompra());
    }
}

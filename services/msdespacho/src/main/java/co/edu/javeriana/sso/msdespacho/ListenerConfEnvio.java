package co.edu.javeriana.sso.msdespacho;

import co.edu.javeriana.sso.msdespacho.modelo.Confirmacion;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.envioConfirmacion. */
@Component
public class ListenerConfEnvio {

    private final SrvDespacho srvDespacho;
    private final JsonMapper json;

    public ListenerConfEnvio(SrvDespacho srvDespacho, JsonMapper json) {
        this.srvDespacho = srvDespacho;
        this.json = json;
    }

    /**
     * Pasos VIII.15b (q.envioConfirmacion → entregar(evento)) y VIII.16b (→ SrvDespacho
     * confirmarEnvio).
     */
    @RabbitListener(queues = "q.envioConfirmacion")
    public void entregar(String evento) {
        srvDespacho.confirmarEnvio(json.readValue(evento, Confirmacion.class).numeroCompra());
    }
}

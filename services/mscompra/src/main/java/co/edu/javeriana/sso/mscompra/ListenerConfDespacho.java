package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.modelo.DespachoConfirmado;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de q.despachoConfirmado (SIN y SEN ya recibieron la compra). Compiten las dos
 * réplicas.
 */
@Component
public class ListenerConfDespacho {

    private final SrvCompraSaga srvCompraSaga;
    private final JsonMapper json;

    public ListenerConfDespacho(SrvCompraSaga srvCompraSaga, JsonMapper json) {
        this.srvCompraSaga = srvCompraSaga;
        this.json = json;
    }

    /**
     * Pasos VIII.21 (q.despachoConfirmado → entregar(evento)) y VIII.22 (→ SrvCompraSaga
     * confirmarDespacho).
     */
    @RabbitListener(queues = "q.despachoConfirmado")
    public void entregar(String evento) {
        srvCompraSaga.confirmarDespacho(
                json.readValue(evento, DespachoConfirmado.class).numeroCompra());
    }
}

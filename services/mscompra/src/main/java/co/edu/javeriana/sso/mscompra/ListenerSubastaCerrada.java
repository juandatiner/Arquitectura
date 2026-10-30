package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.modelo.SubastaCerrada;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.subastaCerrada. Las dos réplicas compiten: cada evento lo recibe solo una. */
@Component
public class ListenerSubastaCerrada {

    private final SrvCompraSaga srvCompraSaga;
    private final JsonMapper json;

    public ListenerSubastaCerrada(SrvCompraSaga srvCompraSaga, JsonMapper json) {
        this.srvCompraSaga = srvCompraSaga;
        this.json = json;
    }

    /**
     * Pasos V.8a/b (q.subastaCerrada → entregar(evento)) y V.9a/b (→ SrvCompraSaga iniciarSaga).
     */
    @RabbitListener(queues = "q.subastaCerrada")
    public void entregar(String evento) {
        srvCompraSaga.iniciarSaga(json.readValue(evento, SubastaCerrada.class));
    }
}

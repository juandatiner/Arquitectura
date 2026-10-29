package co.edu.javeriana.sso.mssubasta;

import co.edu.javeriana.sso.mssubasta.modelo.BienLiberado;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.LiberarBienSubasta (cola de MSSubasta) en la compensación de la compra. */
@Component
public class ListenerLiberarBienSub {

    private final SrvSubasta srvSubasta;
    private final JsonMapper json;

    public ListenerLiberarBienSub(SrvSubasta srvSubasta, JsonMapper json) {
        this.srvSubasta = srvSubasta;
        this.json = json;
    }

    /**
     * Pasos X.5a (q.LiberarBienSubasta → entregar(BienLiberado)) y X.6a (→ SrvSubasta liberarBien).
     */
    @RabbitListener(queues = "q.LiberarBienSubasta.subasta")
    public void entregar(String bienLiberado) {
        BienLiberado evento = json.readValue(bienLiberado, BienLiberado.class);
        srvSubasta.liberarBien(evento.idSubasta(), evento.motivo());
    }
}

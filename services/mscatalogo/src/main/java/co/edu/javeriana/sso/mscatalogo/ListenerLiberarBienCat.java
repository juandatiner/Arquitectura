package co.edu.javeriana.sso.mscatalogo;

import co.edu.javeriana.sso.mscatalogo.modelo.BienLiberado;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.LiberarBienSubasta (cola de MSCatalogo) en la compensación de la compra. */
@Component
public class ListenerLiberarBienCat {

    private final SrvCatalogo srvCatalogo;
    private final JsonMapper json;

    public ListenerLiberarBienCat(SrvCatalogo srvCatalogo, JsonMapper json) {
        this.srvCatalogo = srvCatalogo;
        this.json = json;
    }

    /**
     * Pasos X.5b (q.LiberarBienSubasta → entregar(BienLiberado)) y X.6b (→ SrvCatalogo
     * liberarBien).
     */
    @RabbitListener(queues = "q.LiberarBienSubasta.catalogo")
    public void entregar(String bienLiberado) {
        BienLiberado evento = json.readValue(bienLiberado, BienLiberado.class);
        srvCatalogo.liberarBien(evento.idSubasta(), evento.motivo());
    }
}

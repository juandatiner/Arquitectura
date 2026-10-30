package co.edu.javeriana.sso.mssuscripcion;

import co.edu.javeriana.sso.mssuscripcion.modelo.BienLiberado;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.LiberarBienSubasta (cola de MSSuscripcion) en la compensación de la compra. */
@Component
public class ListenerLiberarBienSus {

    private final SrvSuscripcion srvSuscripcion;
    private final JsonMapper json;

    public ListenerLiberarBienSus(SrvSuscripcion srvSuscripcion, JsonMapper json) {
        this.srvSuscripcion = srvSuscripcion;
        this.json = json;
    }

    /**
     * Pasos X.5c (q.LiberarBienSubasta → entregar(BienLiberado)) y X.6c (→ SrvSuscripcion
     * liberarBien).
     */
    @RabbitListener(queues = "q.LiberarBienSubasta.suscripcion")
    public void entregar(String bienLiberado) {
        BienLiberado evento = json.readValue(bienLiberado, BienLiberado.class);
        srvSuscripcion.liberarBien(evento.idSubasta(), evento.motivo());
    }
}

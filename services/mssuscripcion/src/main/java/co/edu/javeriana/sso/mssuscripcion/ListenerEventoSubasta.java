package co.edu.javeriana.sso.mssuscripcion;

import co.edu.javeriana.sso.mssuscripcion.modelo.EventoSubasta;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.nuevaSubasta, q.subastaIniciada y q.cambioOfertaMayor. */
@Component
public class ListenerEventoSubasta {

    private final SrvSuscripcion srvSuscripcion;
    private final JsonMapper json;

    public ListenerEventoSubasta(SrvSuscripcion srvSuscripcion, JsonMapper json) {
        this.srvSuscripcion = srvSuscripcion;
        this.json = json;
    }

    /**
     * Pasos III.18a / III.18b / IV.16 (q.* → entregar(evento)) y III.19 / IV.17 (→ SrvSuscripcion
     * procesarEvento). El tipo del evento viene en la propiedad AMQP type.
     */
    @RabbitListener(queues = {"q.nuevaSubasta", "q.subastaIniciada", "q.cambioOfertaMayor"})
    public void entregar(Message evento) {
        String tipo = evento.getMessageProperties().getType();
        Class<? extends EventoSubasta> clase =
                switch (tipo == null ? "" : tipo) {
                    case "NuevaSubasta" -> EventoSubasta.NuevaSubasta.class;
                    case "SubastaIniciada" -> EventoSubasta.SubastaIniciada.class;
                    case "CambioOfertaMayor" -> EventoSubasta.CambioOfertaMayor.class;
                    default ->
                            throw new AmqpRejectAndDontRequeueException(
                                    "Tipo de evento desconocido: " + tipo);
                };
        srvSuscripcion.procesarEvento(json.readValue(evento.getBody(), clase));
    }
}

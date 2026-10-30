package co.edu.javeriana.sso.msnotificacion;

import co.edu.javeriana.sso.msnotificacion.modelo.Notificacion;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.notificacion (avisos de Suscripción, Compra y Pago). */
@Component
public class ListenerNotificar {

    private final SrvNotificar srvNotificar;
    private final JsonMapper json;

    public ListenerNotificar(SrvNotificar srvNotificar, JsonMapper json) {
        this.srvNotificar = srvNotificar;
        this.json = json;
    }

    /**
     * Pasos IX.1 (q.notificacion → entregar(evento)) e IX.2 (→ SrvNotificar notificar). El origen
     * (NotificacionGanador, NotificacionPago…) viaja en la propiedad AMQP type.
     */
    @RabbitListener(queues = "q.notificacion")
    public void entregar(Message evento) {
        srvNotificar.notificar(json.readValue(evento.getBody(), Notificacion.class));
    }
}

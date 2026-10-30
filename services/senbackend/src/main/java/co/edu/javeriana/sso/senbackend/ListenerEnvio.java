package co.edu.javeriana.sso.senbackend;

import co.edu.javeriana.sso.senbackend.modelo.SolicitudEnvio;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de q.envioSolicitud. */
@Component
public class ListenerEnvio {

    private final SrvEnvio srvEnvio;
    private final JsonMapper json;

    public ListenerEnvio(SrvEnvio srvEnvio, JsonMapper json) {
        this.srvEnvio = srvEnvio;
        this.json = json;
    }

    /**
     * Pasos VIII.9b (q.envioSolicitud → entregar(evento)) y VIII.10b (→ SrvEnvio programarEnvio).
     */
    @RabbitListener(queues = "q.envioSolicitud")
    public void entregar(String evento) {
        SolicitudEnvio solicitud = json.readValue(evento, SolicitudEnvio.class);
        srvEnvio.programarEnvio(
                solicitud.numeroCompra(), solicitud.idBien(), solicitud.direccion());
    }
}

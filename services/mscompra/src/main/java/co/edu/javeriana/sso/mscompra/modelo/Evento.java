package co.edu.javeriana.sso.mscompra.modelo;

import java.util.UUID;

/**
 * Evento guardado en el outbox: {@code tipo()} viaja en la propiedad AMQP type; {@code cola()} es
 * la q.* del diagrama.
 */
public sealed interface Evento
        permits Notificacion, PagoSolicitud, DespachoSolicitud, BienLiberado {

    UUID idEvento();

    String cola();

    default String tipo() {
        return getClass().getSimpleName();
    }
}

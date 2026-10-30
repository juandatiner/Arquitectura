package co.edu.javeriana.sso.msdespacho.modelo;

import java.util.UUID;

/** Evento guardado en el outbox; {@code cola()} es la cola q.* del diagrama. */
public sealed interface Evento permits SolicitudInventario, SolicitudEnvio, DespachoConfirmado {

    UUID idEvento();

    String cola();
}

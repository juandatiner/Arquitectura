package co.edu.javeriana.sso.sinbackend.modelo;

import java.util.UUID;

/** Evento guardado en el outbox; {@code cola()} es la cola q.* del diagrama. */
public sealed interface Evento permits InventarioConfirmado {

    UUID idEvento();

    String cola();
}

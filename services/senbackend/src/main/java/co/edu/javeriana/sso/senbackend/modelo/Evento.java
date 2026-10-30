package co.edu.javeriana.sso.senbackend.modelo;

import java.util.UUID;

/** Evento guardado en el outbox; {@code cola()} es la cola q.* del diagrama. */
public sealed interface Evento permits EnvioConfirmado {

    UUID idEvento();

    String cola();
}

package co.edu.javeriana.sso.mscatalogo.modelo;

import java.util.UUID;

/**
 * Evento guardado en el outbox. {@code cola()} es la cola q.* del diagrama, o null si no tiene
 * flecha.
 */
public sealed interface Evento permits NuevaSubasta, BienLiberadoConfirmado {

    UUID idEvento();

    String cola();
}

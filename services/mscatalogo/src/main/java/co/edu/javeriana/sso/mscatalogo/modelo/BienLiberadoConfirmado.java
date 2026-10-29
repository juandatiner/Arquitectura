package co.edu.javeriana.sso.mscatalogo.modelo;

import java.util.UUID;

/**
 * Evento de X.8b. El diagrama no le dibuja flecha hacia ninguna cola (observación 8): queda
 * registrado en el outbox como constancia y no se publica.
 */
public record BienLiberadoConfirmado(UUID idEvento, String idSubasta, String motivo)
        implements Evento {

    @Override
    public String cola() {
        return null;
    }
}

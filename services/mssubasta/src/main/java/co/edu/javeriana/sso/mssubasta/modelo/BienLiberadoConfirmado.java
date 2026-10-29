package co.edu.javeriana.sso.mssubasta.modelo;

import java.util.UUID;

/** X.8a. Sin flecha hacia una cola (observación 8): queda REGISTRADO en el outbox. */
public record BienLiberadoConfirmado(UUID idEvento, String idSubasta, String motivo)
        implements Evento {

    @Override
    public String cola() {
        return null;
    }
}

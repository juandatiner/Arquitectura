package co.edu.javeriana.sso.senbackend.modelo;

import java.time.LocalDate;
import java.util.UUID;

/** VIII.12b / VIII.14b → q.envioConfirmacion (lo consume ListenerConfEnvio de MSDespacho). */
public record EnvioConfirmado(UUID idEvento, String numeroCompra, LocalDate fechaProgramada)
        implements Evento {

    @Override
    public String cola() {
        return "q.envioConfirmacion";
    }
}

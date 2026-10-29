package co.edu.javeriana.sso.mscatalogo.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Evento de III.10 / III.12: se publica en q.nuevaSubasta (lo consume ListenerEventoSubasta). */
public record NuevaSubasta(
        UUID idEvento,
        String idSubasta,
        String idBien,
        String descripcion,
        String categoria,
        BigDecimal valorMinimo,
        Instant fechaInicio,
        Instant fechaCierre,
        String publicadaPor)
        implements Evento {

    @Override
    public String cola() {
        return "q.nuevaSubasta";
    }
}

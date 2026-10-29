package co.edu.javeriana.sso.mssubasta.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** III.15 / III.17 → q.subastaIniciada (lo consume ListenerEventoSubasta). */
public record SubastaIniciada(
        UUID idEvento,
        String idSubasta,
        String idBien,
        String descripcion,
        BigDecimal valorMinimo,
        Instant fechaCierre)
        implements Evento {

    @Override
    public String cola() {
        return "q.subastaIniciada";
    }
}

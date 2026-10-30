package co.edu.javeriana.sso.mssubasta.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * IV.13a / IV.15 → q.cambioOfertaMayor. {@code anterior} es quien tenía la oferta mayor (null si es
 * la primera) para avisarle que fue superado.
 */
public record CambioOfertaMayor(
        UUID idEvento,
        String idSubasta,
        String descripcion,
        BigDecimal valor,
        Cliente cliente,
        Cliente anterior)
        implements Evento {

    @Override
    public String cola() {
        return "q.cambioOfertaMayor";
    }
}

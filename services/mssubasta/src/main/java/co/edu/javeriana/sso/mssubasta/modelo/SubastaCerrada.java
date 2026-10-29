package co.edu.javeriana.sso.mssubasta.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * V.5 / V.7 → q.subastaCerrada (la consume ListenerSubastaCerrada en una réplica de MSCompra).
 * {@code ganador} y {@code valor} son null si la subasta quedó DESIERTA.
 */
public record SubastaCerrada(
        UUID idEvento,
        String idSubasta,
        String idBien,
        String descripcion,
        EstadoSubasta resultado,
        Cliente ganador,
        BigDecimal valor)
        implements Evento {

    @Override
    public String cola() {
        return "q.subastaCerrada";
    }
}

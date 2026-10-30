package co.edu.javeriana.sso.mscompra.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Mensaje de q.subastaCerrada (V.7 → V.8a/b). {@code resultado}: ADJUDICADA o DESIERTA. */
public record SubastaCerrada(
        UUID idEvento,
        String idSubasta,
        String idBien,
        String descripcion,
        String resultado,
        Cliente ganador,
        BigDecimal valor) {}

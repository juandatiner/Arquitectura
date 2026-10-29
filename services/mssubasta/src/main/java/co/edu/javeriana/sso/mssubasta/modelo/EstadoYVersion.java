package co.edu.javeriana.sso.mssubasta.modelo;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de obtenerEstadoYVersion(idSubasta) (paso IV.9). */
public record EstadoYVersion(
        String idSubasta,
        String idBien,
        String descripcion,
        BigDecimal valorMinimo,
        Instant fechaInicio,
        Instant fechaCierre,
        EstadoSubasta estado,
        long version) {}

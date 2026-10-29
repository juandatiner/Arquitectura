package co.edu.javeriana.sso.mscatalogo.modelo;

import java.math.BigDecimal;
import java.time.Instant;

/** Una subasta del catálogo con los datos de su bien. */
public record SubastaPublicada(
        String idSubasta,
        String idBien,
        String descripcion,
        String categoria,
        BigDecimal valorMinimo,
        Instant fechaInicio,
        Instant fechaCierre,
        EstadoSubasta estado) {}

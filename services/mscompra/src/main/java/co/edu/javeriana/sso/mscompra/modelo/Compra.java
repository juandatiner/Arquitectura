package co.edu.javeriana.sso.mscompra.modelo;

import java.math.BigDecimal;
import java.time.Instant;

/** Compra de un bien adjudicado (una por subasta). */
public record Compra(
        String numeroCompra,
        String idSubasta,
        String idBien,
        String descripcion,
        Cliente cliente,
        BigDecimal valorOfertado,
        EstadoCompra estado,
        String motivo,
        int intentosOfac,
        Instant creadaEn) {}

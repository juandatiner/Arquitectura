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
        Instant creadaEn) {

    /** Compra CREADA de una subasta adjudicada; la base asigna el número y la fecha (V.10a/b). */
    public static Compra deSubastaCerrada(SubastaCerrada evento) {
        return new Compra(
                null,
                evento.idSubasta(),
                evento.idBien(),
                evento.descripcion(),
                evento.ganador(),
                evento.valor(),
                EstadoCompra.CREADA,
                null,
                0,
                null);
    }
}

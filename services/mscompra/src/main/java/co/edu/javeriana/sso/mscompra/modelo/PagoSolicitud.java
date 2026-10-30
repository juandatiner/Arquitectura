package co.edu.javeriana.sso.mscompra.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** VI.23a / VII.2 → q.pagoSolicitud (Java → .NET, lo consume ListenerPagoSolicitud de MSPago). */
public record PagoSolicitud(
        UUID idEvento,
        String numeroCompra,
        String cedula,
        BigDecimal valorOfertado,
        String descripcion,
        Destinatario destinatario)
        implements Evento {

    @Override
    public String cola() {
        return "q.pagoSolicitud";
    }
}

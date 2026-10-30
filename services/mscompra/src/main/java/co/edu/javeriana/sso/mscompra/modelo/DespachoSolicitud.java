package co.edu.javeriana.sso.mscompra.modelo;

import java.util.UUID;

/** VII.33a / VIII.2 → q.despachoSolicitud (lo consume ListenerDespachoSolicitud de MSDespacho). */
public record DespachoSolicitud(
        UUID idEvento,
        String numeroCompra,
        String idBien,
        String descripcion,
        String direccion,
        Destinatario destinatario)
        implements Evento {

    @Override
    public String cola() {
        return "q.despachoSolicitud";
    }
}

package co.edu.javeriana.sso.msdespacho.modelo;

import java.util.UUID;

/** VIII.6 / VIII.8a → q.inventarioSolicitud (SIN). */
public record SolicitudInventario(UUID idEvento, String numeroCompra, String idBien)
        implements Evento {

    @Override
    public String cola() {
        return "q.inventarioSolicitud";
    }
}

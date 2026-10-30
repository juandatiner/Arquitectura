package co.edu.javeriana.sso.msdespacho.modelo;

import java.util.UUID;

/** VIII.6 / VIII.8b → q.envioSolicitud (SEN). */
public record SolicitudEnvio(UUID idEvento, String numeroCompra, String idBien, String direccion)
        implements Evento {

    @Override
    public String cola() {
        return "q.envioSolicitud";
    }
}

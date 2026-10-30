package co.edu.javeriana.sso.mssuscripcion.modelo;

import java.util.UUID;

/** No existe una suscripción activa con ese id que pertenezca al cliente. */
public class SuscripcionNoEncontradaException extends RuntimeException {

    public SuscripcionNoEncontradaException(UUID idSuscripcion) {
        super("No existe una suscripción activa " + idSuscripcion + " del cliente");
    }
}

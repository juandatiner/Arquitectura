package co.edu.javeriana.sso.msacceso.modelo;

/** No hubo respuesta de AD (caída de red o servidor detenido). */
public class AdNoDisponibleException extends RuntimeException {

    public AdNoDisponibleException(Throwable causa) {
        super("El servicio de autenticación no está disponible", causa);
    }
}

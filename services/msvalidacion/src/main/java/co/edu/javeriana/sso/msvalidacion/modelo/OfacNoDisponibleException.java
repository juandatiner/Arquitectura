package co.edu.javeriana.sso.msvalidacion.modelo;

/** La OFAC no respondió: MSValidacion responde 503 y MSCompra reintenta más tarde (VI.22c). */
public class OfacNoDisponibleException extends RuntimeException {

    public OfacNoDisponibleException(Throwable causa) {
        super("La OFAC no está disponible en este momento", causa);
    }
}

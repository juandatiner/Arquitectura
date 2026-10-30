package co.edu.javeriana.sso.mscompra.modelo;

/** MSValidacion no respondió (caída de red, apagado, tiempo agotado o circuito abierto). */
public class ValidacionNoDisponibleException extends RuntimeException {

    public ValidacionNoDisponibleException(String detalle, Throwable causa) {
        super(detalle, causa);
    }
}

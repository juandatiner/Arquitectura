package co.edu.javeriana.sso.mscatalogo.modelo;

/**
 * La subasta existe pero ya no está PENDIENTE o su fecha de cierre ya pasó (III.9 afecta 0 filas).
 */
public class SubastaNoPublicableException extends RuntimeException {

    public SubastaNoPublicableException(String detalle) {
        super(detalle);
    }
}

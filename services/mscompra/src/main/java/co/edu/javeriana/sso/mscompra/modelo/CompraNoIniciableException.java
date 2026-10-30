package co.edu.javeriana.sso.mscompra.modelo;

public class CompraNoIniciableException extends RuntimeException {

    public CompraNoIniciableException(EstadoCompra estado) {
        super("La compra no se puede iniciar: está " + estado);
    }
}

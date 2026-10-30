package co.edu.javeriana.sso.mscompra.modelo;

public class CompraNoEncontradaException extends RuntimeException {

    public CompraNoEncontradaException(String numeroCompra) {
        super("No existe la compra " + numeroCompra);
    }
}

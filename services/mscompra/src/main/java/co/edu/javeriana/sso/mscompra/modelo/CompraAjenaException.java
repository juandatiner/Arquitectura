package co.edu.javeriana.sso.mscompra.modelo;

/** La compra existe pero no pertenece al cliente autenticado. */
public class CompraAjenaException extends RuntimeException {

    public CompraAjenaException() {
        super("La compra no pertenece al cliente");
    }
}

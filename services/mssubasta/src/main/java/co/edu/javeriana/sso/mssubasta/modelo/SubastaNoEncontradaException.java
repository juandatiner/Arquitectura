package co.edu.javeriana.sso.mssubasta.modelo;

public class SubastaNoEncontradaException extends RuntimeException {

    public SubastaNoEncontradaException(String idSubasta) {
        super("No existe la subasta " + idSubasta);
    }
}

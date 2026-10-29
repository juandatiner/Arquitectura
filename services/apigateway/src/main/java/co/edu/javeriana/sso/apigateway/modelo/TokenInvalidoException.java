package co.edu.javeriana.sso.apigateway.modelo;

/** Falta el token, la firma no es válida o expiró (401). */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String detalle) {
        super(detalle);
    }
}

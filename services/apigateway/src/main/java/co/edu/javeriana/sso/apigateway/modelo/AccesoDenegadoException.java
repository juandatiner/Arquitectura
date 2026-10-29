package co.edu.javeriana.sso.apigateway.modelo;

/** Token válido pero sin permiso para la operación, o credencial de sistema incorrecta (403). */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String detalle) {
        super(detalle);
    }
}

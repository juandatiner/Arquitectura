package co.edu.javeriana.sso.msacceso.modelo;

/** El usuario existe en AD pero no está registrado como cliente de SSO en DB ACCESO. */
public class ClienteNoRegistradoException extends RuntimeException {

    public ClienteNoRegistradoException() {
        super("El usuario no está registrado en SSO");
    }
}

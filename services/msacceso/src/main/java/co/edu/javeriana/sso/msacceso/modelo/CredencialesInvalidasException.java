package co.edu.javeriana.sso.msacceso.modelo;

/** AD rechazó el usuario o la clave. No se dice cuál de los dos falló. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o clave incorrectos");
    }
}

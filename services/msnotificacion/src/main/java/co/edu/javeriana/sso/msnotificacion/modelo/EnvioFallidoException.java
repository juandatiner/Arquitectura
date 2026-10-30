package co.edu.javeriana.sso.msnotificacion.modelo;

/** El servidor de correo o la pasarela SMS no respondió: el mensaje se reintenta desde la cola. */
public class EnvioFallidoException extends RuntimeException {

    public EnvioFallidoException(String detalle, Throwable causa) {
        super(detalle, causa);
    }
}

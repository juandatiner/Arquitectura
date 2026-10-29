package co.edu.javeriana.sso.apigateway.modelo;

/** El componente interno no respondió (caída de red, apagado o tiempo agotado) (503). */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String destino, Throwable causa) {
        super(destino + " no está disponible en este momento", causa);
    }
}

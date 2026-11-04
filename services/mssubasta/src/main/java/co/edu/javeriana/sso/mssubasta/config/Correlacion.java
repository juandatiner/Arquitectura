package co.edu.javeriana.sso.mssubasta.config;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * correlationId de la petición o del evento en curso: viaja en la cabecera HTTP {@value
 * #CABECERA_HTTP} y en la cabecera AMQP {@value #CABECERA_AMQP}, nunca en los cuerpos JSON de los
 * pasos, y sale en cada línea de log.
 */
public final class Correlacion {

    public static final String CABECERA_HTTP = "X-Correlation-Id";
    public static final String CABECERA_AMQP = "correlationId";
    public static final String CLAVE_LOG = "correlationId";

    /** Lo recibido de afuera se valida: evita inyectar texto en los logs. */
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private Correlacion() {}

    /** El correlationId en curso o, si este hilo no tiene ninguno, uno nuevo. */
    public static String actualOGenerar() {
        return normalizar(MDC.get(CLAVE_LOG));
    }

    /** El valor recibido si es válido; si falta o no es válido, uno nuevo. */
    public static String normalizar(Object recibido) {
        return recibido instanceof String texto && VALIDO.matcher(texto).matches()
                ? texto
                : UUID.randomUUID().toString();
    }

    /** Deja el correlationId en el hilo actual hasta cerrar el resultado. */
    public static MDC.MDCCloseable abrir(String correlationId) {
        return MDC.putCloseable(CLAVE_LOG, correlationId);
    }
}

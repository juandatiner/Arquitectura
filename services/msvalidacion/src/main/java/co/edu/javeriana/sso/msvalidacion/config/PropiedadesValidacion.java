package co.edu.javeriana.sso.msvalidacion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param urlOfac WSOfac · OFAC SERVER :9001 (Máquina 3), flecha HTTP/REST de VI.18
 * @param timeoutConexion tiempo máximo para conectar con la OFAC
 * @param timeoutRespuesta tiempo máximo de respuesta de la OFAC
 */
@ConfigurationProperties("sso.validacion")
public record PropiedadesValidacion(
        String urlOfac, Duration timeoutConexion, Duration timeoutRespuesta) {}

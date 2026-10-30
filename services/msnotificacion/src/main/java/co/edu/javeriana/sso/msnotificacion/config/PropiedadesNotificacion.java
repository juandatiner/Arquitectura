package co.edu.javeriana.sso.msnotificacion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param remitente dirección From de los correos
 * @param urlSms SMS GATEWAY · SMS SERVER :9004 (Máquina 3)
 * @param timeoutConexion tiempo máximo para conectar con la pasarela SMS
 * @param timeoutRespuesta tiempo máximo de respuesta de la pasarela SMS
 */
@ConfigurationProperties("sso.notificacion")
public record PropiedadesNotificacion(
        String remitente, String urlSms, Duration timeoutConexion, Duration timeoutRespuesta) {}

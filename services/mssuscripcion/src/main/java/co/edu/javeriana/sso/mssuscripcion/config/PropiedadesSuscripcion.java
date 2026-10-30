package co.edu.javeriana.sso.mssuscripcion.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param zonaHoraria zona en la que se muestran las fechas en los avisos
 */
@ConfigurationProperties("sso.suscripcion")
public record PropiedadesSuscripcion(ZoneId zonaHoraria) {}

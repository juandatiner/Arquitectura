package co.edu.javeriana.sso.senbackend.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param diasParaEnvio días entre la confirmación de la compra y la fecha programada del envío
 * @param zonaHoraria zona para calcular la fecha programada
 */
@ConfigurationProperties("sso.envio")
public record PropiedadesEnvio(int diasParaEnvio, ZoneId zonaHoraria) {}

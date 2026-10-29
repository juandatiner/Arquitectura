package co.edu.javeriana.sso.msacceso.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión a AD SERVER :389.
 *
 * @param url ldap://host:389
 * @param patronDn DN del usuario; {0} se reemplaza por el nombre de usuario
 * @param timeout tiempo máximo de conexión y lectura
 */
@ConfigurationProperties("sso.ad")
public record PropiedadesAD(String url, String patronDn, Duration timeout) {}

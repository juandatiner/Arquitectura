package co.edu.javeriana.sso.msnotificacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Componente MSNotificacion.jar · nodo MSNotificacion SERVER :8089 (Tomcat embebido) · Máquina 2.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MSNotificacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSNotificacionApplication.class, args);
    }
}

package co.edu.javeriana.sso.senbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Componente SENBackEnd.jar · nodo SEN SERVER :9003 (Tomcat embebido) · Máquina 3 · Sistema de
 * Envíos.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SENBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(SENBackEndApplication.class, args);
    }
}

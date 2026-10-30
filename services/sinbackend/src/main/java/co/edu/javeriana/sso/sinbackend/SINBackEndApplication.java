package co.edu.javeriana.sso.sinbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Componente SINBackEnd.jar · nodo SIN SERVER :9002 (Tomcat embebido) · Máquina 3 · Sistema de
 * Inventarios.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SINBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(SINBackEndApplication.class, args);
    }
}

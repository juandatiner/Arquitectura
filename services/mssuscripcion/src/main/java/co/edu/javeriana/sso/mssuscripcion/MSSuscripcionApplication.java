package co.edu.javeriana.sso.mssuscripcion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Componente MSSuscripcion.jar · nodo MSSuscripcion SERVER :8084 (Tomcat embebido) · Máquina 2. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MSSuscripcionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSSuscripcionApplication.class, args);
    }
}

package co.edu.javeriana.sso.msacceso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Componente MSAcceso.jar · nodo MSAcceso SERVER :8081 (Tomcat embebido) · Máquina 2. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MSAccesoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSAccesoApplication.class, args);
    }
}

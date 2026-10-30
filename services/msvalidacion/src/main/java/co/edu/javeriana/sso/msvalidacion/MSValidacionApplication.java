package co.edu.javeriana.sso.msvalidacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Componente MSValidacion.jar · nodo MSValidacion SERVER :8087 (Tomcat embebido) · Máquina 2. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MSValidacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSValidacionApplication.class, args);
    }
}

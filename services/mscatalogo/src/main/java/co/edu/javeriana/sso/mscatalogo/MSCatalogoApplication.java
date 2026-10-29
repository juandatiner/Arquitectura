package co.edu.javeriana.sso.mscatalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Componente MSCatalogo.jar · nodo MSCatalogo SERVER :8082 (Tomcat embebido) · Máquina 2. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MSCatalogoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSCatalogoApplication.class, args);
    }
}

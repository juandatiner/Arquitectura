package co.edu.javeriana.sso.mssubasta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Componente MSSubasta.jar · nodo MSSubasta SERVER :8083 (Tomcat embebido) · Máquina 2. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MSSubastaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSSubastaApplication.class, args);
    }
}

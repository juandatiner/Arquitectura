package co.edu.javeriana.sso.msdespacho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Componente MSDespacho.jar · nodo MSDespacho SERVER :8088 (Tomcat embebido) · Máquina 2. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MSDespachoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSDespachoApplication.class, args);
    }
}

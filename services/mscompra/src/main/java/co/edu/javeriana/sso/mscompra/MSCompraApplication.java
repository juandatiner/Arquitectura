package co.edu.javeriana.sso.mscompra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Componente MSCompra.jar · nodos MSCompra SERVER réplica 1 :8085 (Máquina 2) y réplica 2 :8085
 * (Máquina 3) (Tomcat embebido). Sin estado en memoria: todo vive en DB COMPRA.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MSCompraApplication {

    public static void main(String[] args) {
        SpringApplication.run(MSCompraApplication.class, args);
    }
}

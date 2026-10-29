package co.edu.javeriana.sso.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Componente APIGateway.jar · nodo APIGATEWAY SERVER :8080 (Tomcat embebido) · Máquina 1 (DMZ). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class APIGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(APIGatewayApplication.class, args);
    }
}

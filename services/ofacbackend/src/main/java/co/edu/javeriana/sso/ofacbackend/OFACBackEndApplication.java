package co.edu.javeriana.sso.ofacbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Componente OFACBackEnd.jar · nodo OFAC SERVER :9001 (Tomcat embebido) · Máquina 3 · sistema
 * externo.
 */
@SpringBootApplication
public class OFACBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(OFACBackEndApplication.class, args);
    }
}

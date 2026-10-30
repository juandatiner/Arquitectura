package co.edu.javeriana.sso.senbackend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionEnvio {

    @Bean
    Clock reloj(PropiedadesEnvio propiedades) {
        return Clock.system(propiedades.zonaHoraria());
    }
}

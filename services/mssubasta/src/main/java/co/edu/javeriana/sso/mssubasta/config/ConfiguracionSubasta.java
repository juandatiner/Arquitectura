package co.edu.javeriana.sso.mssubasta.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionSubasta {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}

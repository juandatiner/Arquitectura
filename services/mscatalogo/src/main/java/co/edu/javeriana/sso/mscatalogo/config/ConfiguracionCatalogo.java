package co.edu.javeriana.sso.mscatalogo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionCatalogo {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}

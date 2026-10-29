package co.edu.javeriana.sso.msacceso.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionAcceso {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}

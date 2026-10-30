package co.edu.javeriana.sso.mscompra.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ConfiguracionCompra {

    @Bean
    RestClient restClient(RestClient.Builder builder, PropiedadesCompra propiedades) {
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(propiedades.timeoutConexion()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(propiedades.timeoutRespuesta());
        return builder.requestFactory(fabrica).build();
    }

    /** Se abre tras 5 fallas de las últimas 10 llamadas y deja de llamar 30 s a MSValidacion. */
    @Bean
    CircuitBreaker circuitoValidacion() {
        return CircuitBreaker.of(
                "validacion",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build());
    }
}

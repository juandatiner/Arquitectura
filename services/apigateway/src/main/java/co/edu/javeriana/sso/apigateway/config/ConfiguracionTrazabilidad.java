package co.edu.javeriana.sso.apigateway.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Propaga el correlationId fuera del hilo de una petición HTTP entrante. */
@Configuration
public class ConfiguracionTrazabilidad {

    /** Toda llamada HTTP saliente lleva el correlationId en curso. */
    @Bean
    RestClientCustomizer propagarCorrelacionHttp() {
        return constructor ->
                constructor.requestInterceptor(
                        (peticion, cuerpo, ejecucion) -> {
                            peticion.getHeaders()
                                    .set(Correlacion.CABECERA_HTTP, Correlacion.actualOGenerar());
                            return ejecucion.execute(peticion, cuerpo);
                        });
    }
}

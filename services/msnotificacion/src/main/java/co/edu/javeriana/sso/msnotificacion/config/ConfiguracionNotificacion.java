package co.edu.javeriana.sso.msnotificacion.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ConfiguracionNotificacion {

    @Bean
    RestClient restClient(RestClient.Builder builder, PropiedadesNotificacion propiedades) {
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(propiedades.timeoutConexion()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(propiedades.timeoutRespuesta());
        return builder.requestFactory(fabrica).build();
    }
}

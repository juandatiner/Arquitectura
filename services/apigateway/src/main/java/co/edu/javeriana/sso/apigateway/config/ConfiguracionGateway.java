package co.edu.javeriana.sso.apigateway.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ConfiguracionGateway {

    /** Cliente HTTP de SrvRuteo con tiempos máximos: un componente caído no bloquea el gateway. */
    @Bean
    RestClient restClient(RestClient.Builder builder, PropiedadesGateway propiedades) {
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(propiedades.timeoutConexion()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(propiedades.timeoutRespuesta());
        return builder.requestFactory(fabrica).build();
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    PublicKey clavePublicaToken(PropiedadesGateway propiedades) {
        var archivo = propiedades.token().archivoClavePublica();
        try {
            String pem =
                    Files.readString(archivo)
                            .replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "")
                            .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clave pública inválida en " + archivo, e);
        }
    }
}

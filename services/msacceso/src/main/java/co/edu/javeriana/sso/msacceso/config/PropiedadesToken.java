package co.edu.javeriana.sso.msacceso.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token RS256 que valida SrvSeguridadToken en el APIGateway con la clave pública correspondiente.
 *
 * @param emisor valor del claim iss
 * @param duracion vigencia del token
 * @param archivoClavePrivada PEM PKCS#8 con la clave privada RSA
 */
@ConfigurationProperties("sso.token")
public record PropiedadesToken(String emisor, Duration duracion, Path archivoClavePrivada) {

    public PrivateKey clavePrivada() {
        try {
            String pem =
                    Files.readString(archivoClavePrivada)
                            .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivoClavePrivada, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clave privada inválida en " + archivoClavePrivada, e);
        }
    }
}

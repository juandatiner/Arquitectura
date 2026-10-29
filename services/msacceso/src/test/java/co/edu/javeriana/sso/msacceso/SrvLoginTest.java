package co.edu.javeriana.sso.msacceso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.msacceso.config.PropiedadesToken;
import co.edu.javeriana.sso.msacceso.modelo.CredencialesInvalidasException;
import co.edu.javeriana.sso.msacceso.modelo.DatosCliente;
import co.edu.javeriana.sso.msacceso.modelo.RespuestaAcceso;
import co.edu.javeriana.sso.msacceso.modelo.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SrvLoginTest {

    private static final DatosCliente ANA =
            new DatosCliente(
                    "cliente1",
                    "1010101010",
                    "Ana Gómez",
                    "3001110001",
                    "cliente1@sso.local",
                    "Cra 7",
                    Rol.CLIENTE);

    @TempDir Path temporal;

    private final IntegraLoginAD integraLoginAD = mock(IntegraLoginAD.class);
    private final RepoLogin repoLogin = mock(RepoLogin.class);
    private final Instant ahora = Instant.parse("2026-10-29T12:00:00Z");
    private KeyPair claves;
    private SrvLogin srvLogin;

    @BeforeEach
    void preparar() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        claves = generador.generateKeyPair();
        Path pem = temporal.resolve("privada.pem");
        Files.writeString(
                pem,
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder().encodeToString(claves.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----\n");
        srvLogin =
                new SrvLogin(
                        integraLoginAD,
                        repoLogin,
                        new PropiedadesToken("MSAcceso", Duration.ofMinutes(30), pem),
                        Clock.fixed(ahora, java.time.ZoneOffset.UTC));
    }

    @Test
    void autenticarValidaEnAdObtieneDatosYFirmaElToken() {
        when(integraLoginAD.validarCredenciales("cliente1", "Cliente1*")).thenReturn("cliente1");
        when(repoLogin.obtenerDatosCliente("cliente1")).thenReturn(ANA);

        RespuestaAcceso respuesta = srvLogin.autenticar("cliente1", "Cliente1*");

        Claims claims =
                Jwts.parser()
                        .verifyWith(claves.getPublic())
                        .clock(() -> java.util.Date.from(ahora))
                        .build()
                        .parseSignedClaims(respuesta.token())
                        .getPayload();
        assertThat(claims.getSubject()).isEqualTo("cliente1");
        assertThat(claims.getIssuer()).isEqualTo("MSAcceso");
        assertThat(claims.get("cedula", String.class)).isEqualTo("1010101010");
        assertThat(claims.get("rol", String.class)).isEqualTo("CLIENTE");
        assertThat(respuesta.expira()).isEqualTo(ahora.plus(Duration.ofMinutes(30)));
        assertThat(respuesta.cliente()).isEqualTo(ANA);
    }

    @Test
    void siAdRechazaNoConsultaDbAcceso() {
        when(integraLoginAD.validarCredenciales("cliente1", "mala"))
                .thenThrow(new CredencialesInvalidasException());

        assertThatThrownBy(() -> srvLogin.autenticar("cliente1", "mala"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verifyNoInteractions(repoLogin);
    }
}

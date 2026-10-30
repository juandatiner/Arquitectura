package co.edu.javeriana.sso.apigateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import co.edu.javeriana.sso.apigateway.config.PropiedadesGateway;
import co.edu.javeriana.sso.apigateway.modelo.AccesoDenegadoException;
import co.edu.javeriana.sso.apigateway.modelo.IdentidadCliente;
import co.edu.javeriana.sso.apigateway.modelo.Solicitud;
import co.edu.javeriana.sso.apigateway.modelo.TokenInvalidoException;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SrvSeguridadTokenTest {

    private final Instant ahora = Instant.parse("2026-10-29T12:00:00Z");
    private final SrvRuteo srvRuteo = mock(SrvRuteo.class);
    private KeyPair claves;
    private SrvSeguridadToken srvSeguridadToken;

    @BeforeEach
    void preparar() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        claves = generador.generateKeyPair();
        PropiedadesGateway propiedades =
                new PropiedadesGateway(
                        null,
                        new PropiedadesGateway.Token("MSAcceso", Path.of("no-usado")),
                        "secreto-yopago",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1));
        srvSeguridadToken =
                new SrvSeguridadToken(
                        srvRuteo,
                        claves.getPublic(),
                        propiedades,
                        Clock.fixed(ahora, ZoneOffset.UTC));
    }

    private String token(String rol, Instant expira, java.security.PrivateKey firma) {
        return "Bearer "
                + Jwts.builder()
                        .issuer("MSAcceso")
                        .subject("cliente1")
                        .claim("cedula", "1010101010")
                        .claim("rol", rol)
                        .issuedAt(Date.from(ahora.minusSeconds(60)))
                        .expiration(Date.from(expira))
                        .signWith(firma)
                        .compact();
    }

    @Test
    void ofertaConTokenValidoSeEnrutaConLaIdentidadDelToken() {
        String token = token("CLIENTE", ahora.plusSeconds(600), claves.getPrivate());

        srvSeguridadToken.validarYAutorizar(
                new Solicitud.Oferta("S-1", new BigDecimal("1500000"), token));

        verify(srvRuteo)
                .enrutarOferta(
                        eq("S-1"),
                        eq(
                                new IdentidadCliente(
                                        "cliente1",
                                        "1010101010",
                                        null,
                                        null,
                                        null,
                                        null,
                                        "CLIENTE")),
                        eq(new BigDecimal("1500000")));
    }

    @Test
    void sinTokenNoEntraALaRedInterna() {
        assertThatThrownBy(
                        () ->
                                srvSeguridadToken.validarYAutorizar(
                                        new Solicitud.InicioCompra("C-1", null)))
                .isInstanceOf(TokenInvalidoException.class);
        verifyNoInteractions(srvRuteo);
    }

    @Test
    void tokenExpiradoEsRechazado() {
        String token = token("CLIENTE", ahora.minusSeconds(1), claves.getPrivate());
        assertThatThrownBy(
                        () ->
                                srvSeguridadToken.validarYAutorizar(
                                        new Solicitud.ConsultaCompras(token)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("expiró");
        verifyNoInteractions(srvRuteo);
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        String token =
                token("ADMIN", ahora.plusSeconds(600), generador.generateKeyPair().getPrivate());
        assertThatThrownBy(
                        () ->
                                srvSeguridadToken.validarYAutorizar(
                                        new Solicitud.Publicacion("S-1", token)))
                .isInstanceOf(TokenInvalidoException.class);
        verifyNoInteractions(srvRuteo);
    }

    @Test
    void clienteNoPuedePublicarSubastas() {
        String token = token("CLIENTE", ahora.plusSeconds(600), claves.getPrivate());
        assertThatThrownBy(
                        () ->
                                srvSeguridadToken.validarYAutorizar(
                                        new Solicitud.Publicacion("S-1", token)))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(srvRuteo);
    }

    @Test
    void adminPuedePublicar() {
        String token = token("ADMIN", ahora.plusSeconds(600), claves.getPrivate());
        srvSeguridadToken.validarYAutorizar(new Solicitud.Publicacion("S-1", token));
        verify(srvRuteo).enrutarPublicacion(eq("S-1"), any());
    }

    @Test
    void reportePagoExigeLaCredencialDeYoPago() {
        assertThatThrownBy(
                        () ->
                                srvSeguridadToken.validarYAutorizar(
                                        new Solicitud.ReportePago(
                                                "1010101010", "C-1", BigDecimal.TEN, "otra")))
                .isInstanceOf(AccesoDenegadoException.class);
        srvSeguridadToken.validarYAutorizar(
                new Solicitud.ReportePago("1010101010", "C-1", BigDecimal.TEN, "secreto-yopago"));
        verify(srvRuteo).enrutarReportePago("1010101010", "C-1", BigDecimal.TEN);
    }
}

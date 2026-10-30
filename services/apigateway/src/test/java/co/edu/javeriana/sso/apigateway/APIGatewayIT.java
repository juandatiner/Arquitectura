package co.edu.javeriana.sso.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gateway de punta a punta: Nginx → WSGateway → SrvSeguridadToken → SrvRuteo → componente interno
 * (simulado con un servidor HTTP que registra lo que recibe).
 */
@SpringBootTest(
        properties = {
            "RUTA_MSACCESO=x",
            "RUTA_MSCATALOGO=x",
            "RUTA_MSSUBASTA=x",
            "RUTA_MSSUSCRIPCION=x",
            "RUTA_BALANCEADOR_COMPRA=x",
            "RUTA_MSPAGO=x",
            "TOKEN_CLAVE_PUBLICA=x",
            "CREDENCIAL_YOPAGO=x"
        })
@AutoConfigureMockMvc
class APIGatewayIT {

    static final AtomicReference<String> RECIBIDO = new AtomicReference<>();
    static final HttpServer INTERNO;
    static final KeyPair CLAVES;

    static {
        try {
            INTERNO = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            INTERNO.createContext(
                    "/",
                    intercambio -> {
                        RECIBIDO.set(
                                intercambio.getRequestMethod()
                                        + " "
                                        + intercambio.getRequestURI()
                                        + " "
                                        + new String(
                                                intercambio.getRequestBody().readAllBytes(),
                                                StandardCharsets.UTF_8));
                        byte[] respuesta = "{\"estado\":\"REGISTRADA\"}".getBytes();
                        intercambio.getResponseHeaders().add("Content-Type", "application/json");
                        intercambio.sendResponseHeaders(202, respuesta.length);
                        intercambio.getResponseBody().write(respuesta);
                        intercambio.close();
                    });
            INTERNO.start();
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            CLAVES = generador.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void detener() {
        INTERNO.stop(0);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws Exception {
        String interno = "http://127.0.0.1:" + INTERNO.getAddress().getPort();
        registro.add("sso.rutas.ms-acceso", () -> interno);
        registro.add("sso.rutas.ms-catalogo", () -> interno);
        registro.add("sso.rutas.ms-subasta", () -> interno);
        registro.add("sso.rutas.ms-suscripcion", () -> "http://127.0.0.1:1");
        registro.add("sso.rutas.balanceador-compra", () -> interno);
        registro.add("sso.rutas.ms-pago", () -> interno);
        registro.add("sso.credencial-yo-pago", () -> "secreto-yopago");
        Path pem = Files.createTempFile("publica", ".pem");
        Files.writeString(
                pem,
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder().encodeToString(CLAVES.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n");
        registro.add("sso.token.archivo-clave-publica", pem::toString);
    }

    @Autowired MockMvc mvc;

    private static String token() {
        return "Bearer "
                + Jwts.builder()
                        .issuer("MSAcceso")
                        .subject("cliente1")
                        .claim("cedula", "1010101010")
                        .claim("celular", "3001110001")
                        .claim("rol", "CLIENTE")
                        .expiration(Date.from(Instant.now().plusSeconds(600)))
                        .signWith(CLAVES.getPrivate())
                        .compact();
    }

    @Test
    void loginEsPublicoYPasaTalCualAMSAcceso() throws Exception {
        mvc.perform(
                        post("/api/acceso/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"usuario\":\"cliente1\",\"clave\":\"Cliente1*\"}"))
                .andExpect(status().isAccepted());
        assertThat(RECIBIDO.get())
                .isEqualTo("POST /acceso/login {\"usuario\":\"cliente1\",\"clave\":\"Cliente1*\"}");
    }

    @Test
    void ofertaLlegaAWSSubastaConElClienteDelTokenYNoDelCuerpo() throws Exception {
        mvc.perform(
                        post("/api/subastas/S-7/ofertas")
                                .header(HttpHeaders.AUTHORIZATION, token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"valor\":2500000,\"cliente\":{\"cedula\":\"999\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"estado\":\"REGISTRADA\"}"));
        assertThat(RECIBIDO.get())
                .startsWith("POST /subastas/ofertas {\"idSubasta\":\"S-7\",\"cliente\":{")
                .contains("\"cedula\":\"1010101010\"")
                .doesNotContain("999")
                .endsWith("\"valor\":2500000}");
    }

    @Test
    void inicioDeCompraVaPorElBalanceador() throws Exception {
        mvc.perform(post("/api/compras/C-42/iniciar").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isAccepted());
        assertThat(RECIBIDO.get()).startsWith("POST /compras/iniciar {\"numeroCompra\":\"C-42\"");
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(post("/api/compras/C-42/iniciar")).andExpect(status().isUnauthorized());
    }

    @Test
    void componenteInternoCaidoDevuelve503SinTumbarElGateway() throws Exception {
        mvc.perform(
                        post("/api/suscripciones")
                                .header(HttpHeaders.AUTHORIZATION, token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipo\":\"CATEGORIA\",\"valor\":\"VEHICULOS\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.detail")
                                .value("MSSuscripcion no está disponible en este momento"));
    }
}

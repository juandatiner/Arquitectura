package co.edu.javeriana.sso.msacceso;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** CU I de punta a punta dentro de MSAcceso: AD real (OpenLDAP) y DB ACCESO real (PostgreSQL). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MSAccesoIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_ACCESO = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> AD =
            new GenericContainer<>("osixia/openldap:1.5.0")
                    .withCommand("--copy-service")
                    .withEnv("LDAP_DOMAIN", "sso.javeriana.edu.co")
                    .withEnv("LDAP_ADMIN_PASSWORD", "admin")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../../deploy/ad/ldif/01-usuarios.ldif"),
                            "/container/service/slapd/assets/config/bootstrap/ldif/custom/01-usuarios.ldif")
                    .withExposedPorts(389)
                    .waitingFor(Wait.forLogMessage(".*slapd starting.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws Exception {
        registro.add("sso.ad.url", () -> "ldap://" + AD.getHost() + ":" + AD.getMappedPort(389));
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        Path pem = Files.createTempFile("token", ".pem");
        Files.writeString(
                pem,
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder()
                                .encodeToString(
                                        generador.generateKeyPair().getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----\n");
        registro.add("sso.token.archivo-clave-privada", pem::toString);
    }

    @Autowired MockMvc mvc;

    @Test
    void credencialesValidasDevuelvenTokenYDatosDelCliente() throws Exception {
        mvc.perform(login("cliente1", "Cliente1*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.cliente.cedula").value("1010101010"))
                .andExpect(jsonPath("$.cliente.rol").value("CLIENTE"));
    }

    @Test
    void claveIncorrectaDevuelve401() throws Exception {
        mvc.perform(login("cliente1", "incorrecta")).andExpect(status().isUnauthorized());
    }

    @Test
    void claveVaciaNoHaceBindAnonimo() throws Exception {
        mvc.perform(login("cliente1", "")).andExpect(status().isBadRequest());
    }

    @Test
    void usuarioConCaracteresDeDnDevuelve401() throws Exception {
        mvc.perform(login("*)(uid=*", "x")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(
            String usuario, String clave) {
        return post("/acceso/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + clave + "\"}");
    }
}

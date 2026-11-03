package co.edu.javeriana.sso.ofacbackend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** VI.18 – VI.20 con DB OFAC (PostgreSQL) real. */
@SpringBootTest(
        properties = {"DB_URL=jdbc:postgresql://reemplazado/x", "DB_USUARIO=x", "DB_CLAVE=x"})
@AutoConfigureMockMvc
@Testcontainers
@Sql("/lista-prueba.sql")
class OFACBackEndIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_OFAC = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mvc;

    @Test
    void cedulaSinReporteEstaAprobada() throws Exception {
        mvc.perform(get("/ofac/1010101010")).andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void cedulaReportadaEsRechazada() throws Exception {
        mvc.perform(get("/ofac/7777777777")).andExpect(jsonPath("$.estado").value("RECHAZADO"));
    }

    @Test
    void cedulaEnEstudioEstaEnProceso() throws Exception {
        mvc.perform(get("/ofac/8888888888")).andExpect(jsonPath("$.estado").value("ENPROCESO"));
    }

    @Test
    void cedulaInvalidaDevuelve400() throws Exception {
        mvc.perform(get("/ofac/abc")).andExpect(status().isBadRequest());
    }

    /** La documentación de la API no existe fuera del perfil dev (#25). */
    @Test
    void sinPerfilDevLaDocumentacionDeLaApiNoExiste() throws Exception {
        for (String ruta :
                List.of(
                        "/v3/api-docs",
                        "/swagger-ui.html",
                        "/swagger-ui/index.html",
                        "/webjars/swagger-ui/index.html")) {
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @ActiveProfiles("dev")
    class ConPerfilDev {

        @Autowired MockMvc mvcDev;

        @Test
        void publicaOpenApiYSwaggerUi() throws Exception {
            mvcDev.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/ofac/{cedula}']").exists());
            mvcDev.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}

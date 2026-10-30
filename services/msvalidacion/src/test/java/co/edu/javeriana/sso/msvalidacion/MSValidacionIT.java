package co.edu.javeriana.sso.msvalidacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** VI.14 – VI.21 con DB VALIDAR (PostgreSQL) real y una OFAC simulada que cuenta las consultas. */
@SpringBootTest(
        properties = {"DB_URL=jdbc:postgresql://reemplazado/x", "DB_USUARIO=x", "DB_CLAVE=x"})
@AutoConfigureMockMvc
@Testcontainers
class MSValidacionIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_VALIDAR = new PostgreSQLContainer("postgres:17-alpine");

    static final AtomicInteger CONSULTAS = new AtomicInteger();
    static final AtomicBoolean CAIDA = new AtomicBoolean(false);
    static final HttpServer OFAC;

    static {
        try {
            OFAC = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            OFAC.createContext(
                    "/ofac/",
                    x -> {
                        CONSULTAS.incrementAndGet();
                        if (CAIDA.get()) {
                            x.sendResponseHeaders(503, -1);
                        } else {
                            String cedula =
                                    x.getRequestURI().getPath().substring("/ofac/".length());
                            byte[] r =
                                    ("{\"cedula\":\"" + cedula + "\",\"estado\":\"APROBADO\"}")
                                            .getBytes();
                            x.getResponseHeaders().add("Content-Type", "application/json");
                            x.sendResponseHeaders(200, r.length);
                            x.getResponseBody().write(r);
                        }
                        x.close();
                    });
            OFAC.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void detener() {
        OFAC.stop(0);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("RUTA_OFAC", () -> "http://127.0.0.1:" + OFAC.getAddress().getPort());
    }

    @Autowired MockMvc mvc;

    @Test
    void aprobadoSeGuardaYNoSeVuelveAConsultar() throws Exception {
        CAIDA.set(false);
        int antes = CONSULTAS.get();
        for (int i = 0; i < 2; i++) {
            mvc.perform(validar("C-2026-000100", "1010101010"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADO"));
        }
        assertThat(CONSULTAS.get() - antes).isEqualTo(1);
    }

    @Test
    void ofacCaidaDevuelve503() throws Exception {
        CAIDA.set(true);
        mvc.perform(validar("C-2026-000200", "2020202020"))
                .andExpect(status().isServiceUnavailable());
        CAIDA.set(false);
    }

    private static org.springframework.test.web.servlet.RequestBuilder validar(
            String numero, String cedula) {
        return post("/validaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numeroCompra\":\"" + numero + "\",\"cedula\":\"" + cedula + "\"}");
    }
}

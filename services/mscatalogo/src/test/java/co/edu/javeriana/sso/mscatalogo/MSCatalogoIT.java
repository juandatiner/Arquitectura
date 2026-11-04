package co.edu.javeriana.sso.mscatalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.MDC;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** CU II, III y X.5b – X.8b con DB CATALOGO (PostgreSQL) y MOM SERVER (RabbitMQ) reales. */
@SpringBootTest(
        properties = {
            "DB_URL=jdbc:postgresql://reemplazado-por-testcontainers/x",
            "DB_USUARIO=x",
            "DB_CLAVE=x",
            "MOM_HOST=reemplazado-por-testcontainers",
            "MOM_USUARIO=x",
            "MOM_CLAVE=x"
        })
@AutoConfigureMockMvc
@Testcontainers
@Sql("/subastas-prueba.sql")
@Import(MSCatalogoIT.Colas.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MSCatalogoIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_CATALOGO = new PostgreSQLContainer("postgres:17-alpine");

    @Container @ServiceConnection
    static final RabbitMQContainer MOM = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    /** Mismas colas que deploy/mom/definiciones/colas.json. */
    @TestConfiguration
    static class Colas {
        @Bean
        FanoutExchange nuevaSubasta() {
            return new FanoutExchange("q.nuevaSubasta");
        }

        @Bean
        Queue colaNuevaSubasta() {
            return new Queue("q.nuevaSubasta");
        }

        @Bean
        org.springframework.amqp.core.Binding enlace() {
            return BindingBuilder.bind(colaNuevaSubasta()).to(nuevaSubasta());
        }

        @Bean
        Queue colaLiberarBienCatalogo() {
            return new Queue("q.LiberarBienSubasta.catalogo");
        }
    }

    private static final String ADMIN =
            "{\"usuario\":\"admin1\",\"cedula\":\"1090909090\",\"nombre\":\"Administrador\",\"rol\":\"ADMIN\"}";

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired JdbcClient jdbc;
    @Autowired TaskScheduler programador;

    @Test
    @Order(1)
    void elCatalogoSoloMuestraSubastasPublicadas() throws Exception {
        mvc.perform(get("/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @Order(2)
    void publicarCambiaElEstadoYEmiteNuevaSubastaEnLaCola() throws Exception {
        mvc.perform(publicar("SUB-0001").header("X-Correlation-Id", "desde-gateway-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM subasta WHERE id_subasta = 'SUB-0001'"
                                                + " AND publicada_por = 'admin1' AND publicada_en IS NOT NULL")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);

        Message mensaje = rabbit.receive("q.nuevaSubasta", 10_000);
        assertThat(mensaje).isNotNull();
        assertThat(new String(mensaje.getBody()))
                .contains("\"idSubasta\":\"SUB-0001\"")
                .contains("\"publicadaPor\":\"admin1\"");
        assertThat(mensaje.getMessageProperties().getType()).isEqualTo("NuevaSubasta");
        // HTTP → outbox → AMQP: el correlationId cruza en cabecera, no en el cuerpo del evento
        assertThat(mensaje.getMessageProperties().<String>getHeader("correlationId"))
                .isEqualTo("desde-gateway-1");
        assertThat(new String(mensaje.getBody())).doesNotContain("desde-gateway-1");
        await().atMost(Duration.ofSeconds(5))
                .until(
                        () ->
                                jdbc.sql("SELECT estado FROM outbox WHERE tipo = 'NuevaSubasta'")
                                        .query(String.class)
                                        .single()
                                        .equals("ENVIADO"));

        mvc.perform(get("/catalogo").param("categoria", "VEHICULOS"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].idSubasta").value("SUB-0001"));
    }

    @Test
    @Order(3)
    void publicarDosVecesDevuelve409YNoDuplicaElEvento() throws Exception {
        mvc.perform(publicar("SUB-0001")).andExpect(status().isConflict());
        assertThat(
                        jdbc.sql("SELECT count(*) FROM outbox WHERE tipo = 'NuevaSubasta'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    @Order(4)
    void publicarUnaInexistenteDevuelve404() throws Exception {
        mvc.perform(publicar("SUB-9999")).andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void bienLiberadoMarcaLaSubastaLiberadaUnaSolaVez() {
        String bienLiberado =
                "{\"idEvento\":\""
                        + UUID.randomUUID()
                        + "\",\"numeroCompra\":\"C-1\",\"idSubasta\":\"SUB-0001\",\"motivo\":\"pago rechazado\"}";
        MessagePostProcessor conCorrelacion =
                m -> {
                    m.getMessageProperties().setHeader("correlationId", "desde-compra-1");
                    return m;
                };
        rabbit.convertAndSend("", "q.LiberarBienSubasta.catalogo", bienLiberado, conCorrelacion);
        rabbit.convertAndSend("", "q.LiberarBienSubasta.catalogo", bienLiberado, conCorrelacion);

        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                jdbc.sql("SELECT estado FROM subasta WHERE id_subasta = 'SUB-0001'")
                                        .query(String.class)
                                        .single()
                                        .equals("LIBERADA"));
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .until(
                        () ->
                                jdbc.sql(
                                                        "SELECT count(*) FROM outbox WHERE tipo = 'BienLiberadoConfirmado'"
                                                                + " AND estado = 'REGISTRADO'")
                                                .query(Long.class)
                                                .single()
                                        == 1);
        // AMQP → listener → outbox: el evento que sigue conserva el correlationId del que lo causó
        assertThat(
                        jdbc.sql(
                                        "SELECT correlation_id FROM outbox"
                                                + " WHERE tipo = 'BienLiberadoConfirmado'")
                                .query(String.class)
                                .single())
                .isEqualTo("desde-compra-1");
    }

    /** Observación 16: una subasta nueva del mismo bien lo devuelve al catálogo. */
    @Test
    @Order(6)
    void unaSubastaNuevaDelBienLiberadoLoDevuelveAlCatalogo() throws Exception {
        mvc.perform(get("/catalogo").param("categoria", "VEHICULOS"))
                .andExpect(jsonPath("$.total").value(0));

        jdbc.sql(
                        "INSERT INTO subasta (id_subasta, id_bien, fecha_inicio, fecha_cierre, estado)"
                                + " VALUES ('SUB-0002', 'BIEN-0001', now() + interval '1 day',"
                                + " now() + interval '4 days', 'PENDIENTE')")
                .update();
        mvc.perform(publicar("SUB-0002")).andExpect(status().isOk());

        mvc.perform(get("/catalogo").param("categoria", "VEHICULOS"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].idSubasta").value("SUB-0002"));
    }

    @Test
    @Order(7)
    void unaSubastaYaCerradaNoSaleEnElCatalogo() throws Exception {
        jdbc.sql(
                        "INSERT INTO subasta (id_subasta, id_bien, fecha_inicio, fecha_cierre, estado)"
                                + " VALUES ('SUB-CERRADA', 'BIEN-0001', now() - interval '2 days',"
                                + " now() - interval '1 day', 'PUBLICADA')")
                .update();

        mvc.perform(get("/catalogo").param("categoria", "VEHICULOS"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].idSubasta").value("SUB-0002"));
    }

    @Test
    @Order(6)
    void lasTareasProgramadasCorrenConSuPropioCorrelationId() {
        AtomicReference<String> visto = new AtomicReference<>();

        programador.schedule(() -> visto.set(MDC.get("correlationId")), Instant.now());

        await().atMost(Duration.ofSeconds(5)).until(() -> visto.get() != null);
    }

    private static MockHttpServletRequestBuilder publicar(String idSubasta) {
        return post("/catalogo/publicar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSubasta\":\"" + idSubasta + "\",\"admin\":" + ADMIN + "}");
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
                    .andExpect(jsonPath("$.paths['/catalogo']").exists())
                    .andExpect(
                            jsonPath("$.paths['/catalogo'].get.parameters[?(@.name=='texto')]")
                                    .exists());
            mvcDev.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}

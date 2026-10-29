package co.edu.javeriana.sso.mscatalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** CU II, III y X.5b – X.8b con DB CATALOGO (PostgreSQL) y MOM SERVER (RabbitMQ) reales. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
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
        mvc.perform(publicar("SUB-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));

        Message mensaje = rabbit.receive("q.nuevaSubasta", 10_000);
        assertThat(mensaje).isNotNull();
        assertThat(new String(mensaje.getBody()))
                .contains("\"idSubasta\":\"SUB-0001\"")
                .contains("\"publicadaPor\":\"admin1\"");
        assertThat(mensaje.getMessageProperties().getType()).isEqualTo("NuevaSubasta");
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
    void bienLiberadoDevuelveElBienAlCatalogoUnaSolaVez() {
        String bienLiberado =
                "{\"idEvento\":\""
                        + UUID.randomUUID()
                        + "\",\"numeroCompra\":\"C-1\",\"idSubasta\":\"SUB-0001\",\"motivo\":\"pago rechazado\"}";
        rabbit.convertAndSend("", "q.LiberarBienSubasta.catalogo", bienLiberado);
        rabbit.convertAndSend("", "q.LiberarBienSubasta.catalogo", bienLiberado);

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
    }

    private static org.springframework.test.web.servlet.RequestBuilder publicar(String idSubasta) {
        return post("/catalogo/publicar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSubasta\":\"" + idSubasta + "\",\"admin\":" + ADMIN + "}");
    }
}

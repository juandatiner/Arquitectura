package co.edu.javeriana.sso.mssuscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
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

/**
 * CU II (suscribirse/cancelar), III/IV (avisos a suscriptores) y X.5c – X.8c con PostgreSQL y
 * RabbitMQ reales.
 */
@SpringBootTest(
        properties = {
            "DB_URL=jdbc:postgresql://reemplazado-por-testcontainers/x",
            "DB_USUARIO=x",
            "DB_CLAVE=x",
            "MOM_HOST=reemplazado-por-testcontainers",
            "MOM_USUARIO=x",
            "MOM_CLAVE=x",
            "sso.outbox.intervalo=200ms"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import(MSSuscripcionIT.Colas.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MSSuscripcionIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_SUSCRIPCION = new PostgreSQLContainer("postgres:17-alpine");

    @Container @ServiceConnection
    static final RabbitMQContainer MOM = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    /** Mismas colas que deploy/mom/definiciones/colas.json. */
    @TestConfiguration
    static class Colas {
        @Bean
        Declarables colas() {
            List<Declarable> d = new ArrayList<>();
            for (String nombre :
                    List.of(
                            "q.nuevaSubasta",
                            "q.subastaIniciada",
                            "q.cambioOfertaMayor",
                            "q.notificacion")) {
                FanoutExchange exchange = new FanoutExchange(nombre);
                Queue cola = new Queue(nombre);
                d.addAll(List.of(exchange, cola, BindingBuilder.bind(cola).to(exchange)));
            }
            d.add(new Queue("q.LiberarBienSubasta.suscripcion"));
            return new Declarables(d);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    static String idSuscripcionCambios;

    @Test
    @Order(1)
    void suscribirseEsIdempotente() throws Exception {
        mvc.perform(suscribir("1111", "NUEVAS_SUBASTAS", "VEHICULOS"))
                .andExpect(status().isCreated());
        mvc.perform(suscribir("2222", "NUEVAS_SUBASTAS", "TODAS")).andExpect(status().isCreated());
        String primera =
                mvc.perform(suscribir("3333", "CAMBIOS_OFERTA", "SUB-0001"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String segunda =
                mvc.perform(suscribir("3333", "CAMBIOS_OFERTA", "SUB-0001"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        idSuscripcionCambios = primera.replaceAll(".*\"idSuscripcion\":\"([^\"]+)\".*", "$1");
        assertThat(segunda).contains(idSuscripcionCambios);
    }

    @Test
    @Order(2)
    void nuevaSubastaAvisaAlSuscriptorDeLaCategoriaYAlDeTodasUnaSolaVez() {
        UUID idEvento = UUID.randomUUID();
        String nueva =
                "{\"idEvento\":\""
                        + idEvento
                        + "\",\"idSubasta\":\"SUB-0001\",\"idBien\":\"BIEN-0001\","
                        + "\"descripcion\":\"Camioneta\",\"categoria\":\"VEHICULOS\",\"valorMinimo\":95000000,"
                        + "\"fechaInicio\":\"2026-11-01T13:00:00Z\",\"fechaCierre\":\"2026-11-04T23:00:00Z\","
                        + "\"publicadaPor\":\"admin1\"}";
        publicar("q.nuevaSubasta", "NuevaSubasta", nueva);
        publicar("q.nuevaSubasta", "NuevaSubasta", nueva);

        List<String> avisos = recibir(2);
        assertThat(avisos)
                .anyMatch(a -> a.contains("1111@sso.local"))
                .anyMatch(a -> a.contains("2222@sso.local"));
        assertThat(rabbit.receive("q.notificacion", 1500)).isNull();
    }

    @Test
    @Order(3)
    void cambioDeOfertaMayorAvisaPorSmsAlSuscritoALaSubasta() {
        String cambio =
                "{\"idEvento\":\""
                        + UUID.randomUUID()
                        + "\",\"idSubasta\":\"SUB-0001\",\"descripcion\":\"Camioneta\","
                        + "\"valor\":99000000,\"cliente\":{\"usuario\":\"u9\",\"cedula\":\"9999\"},\"anterior\":null}";
        publicar("q.cambioOfertaMayor", "CambioOfertaMayor", cambio);

        String aviso = recibir(1).get(0);
        assertThat(aviso).contains("3003333").contains("\"sms\":true").contains("99.000.000");
    }

    @Test
    @Order(4)
    void soloElDuenoPuedeCancelar() throws Exception {
        mvc.perform(cancelar(idSuscripcionCambios, "1111")).andExpect(status().isNotFound());
        mvc.perform(cancelar(idSuscripcionCambios, "3333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    @Order(5)
    void bienLiberadoLiberaLaSubastaConocida() {
        rabbit.convertAndSend(
                "",
                "q.LiberarBienSubasta.suscripcion",
                "{\"idEvento\":\""
                        + UUID.randomUUID()
                        + "\",\"numeroCompra\":\"C-1\",\"idSubasta\":\"SUB-0001\",\"motivo\":\"pago rechazado\"}");
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                jdbc.sql(
                                                "SELECT estado FROM subasta_conocida WHERE id_subasta = 'SUB-0001'")
                                        .query(String.class)
                                        .single()
                                        .equals("LIBERADA"));
    }

    private void publicar(String cola, String tipo, String json) {
        Message mensaje =
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setType(tipo)
                        .build();
        rabbit.send(cola, "", mensaje);
    }

    private List<String> recibir(int cantidad) {
        List<String> avisos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            Message m = rabbit.receive("q.notificacion", 10_000);
            assertThat(m).as("aviso %d en q.notificacion", i + 1).isNotNull();
            avisos.add(new String(m.getBody(), StandardCharsets.UTF_8));
        }
        return avisos;
    }

    private static String cliente(String cedula) {
        return "{\"usuario\":\"u"
                + cedula
                + "\",\"cedula\":\""
                + cedula
                + "\",\"nombre\":\"Cliente "
                + cedula
                + "\",\"celular\":\"300"
                + cedula
                + "\",\"correo\":\""
                + cedula
                + "@sso.local\"}";
    }

    private static org.springframework.test.web.servlet.RequestBuilder suscribir(
            String cedula, String tipo, String valor) {
        return post("/suscripciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"criterio\":{\"tipo\":\""
                                + tipo
                                + "\",\"valor\":\""
                                + valor
                                + "\"},\"cliente\":"
                                + cliente(cedula)
                                + "}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder cancelar(
            String id, String cedula) {
        return post("/suscripciones/cancelar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSuscripcion\":\"" + id + "\",\"cliente\":" + cliente(cedula) + "}");
    }
}

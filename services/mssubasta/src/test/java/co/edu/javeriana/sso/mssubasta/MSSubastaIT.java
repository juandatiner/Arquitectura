package co.edu.javeriana.sso.mssubasta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
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
 * CU III (apertura), IV (ofertas concurrentes), V (cierre) y X.5a – X.8a con PostgreSQL y RabbitMQ
 * reales.
 */
@SpringBootTest(
        properties = {
            "sso.timers.apertura=300ms",
            "sso.timers.cierre=300ms",
            "sso.outbox.intervalo=200ms"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import(MSSubastaIT.Colas.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MSSubastaIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_SUBASTA = new PostgreSQLContainer("postgres:17-alpine");

    @Container @ServiceConnection
    static final RabbitMQContainer MOM = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    /** Mismas colas que deploy/mom/definiciones/colas.json. */
    @TestConfiguration
    static class Colas {
        @Bean
        Declarables colas() {
            List<org.springframework.amqp.core.Declarable> d = new ArrayList<>();
            for (String nombre :
                    List.of("q.subastaIniciada", "q.cambioOfertaMayor", "q.subastaCerrada")) {
                FanoutExchange exchange = new FanoutExchange(nombre);
                Queue cola = new Queue(nombre);
                Binding enlace = BindingBuilder.bind(cola).to(exchange);
                d.addAll(List.of(exchange, cola, enlace));
            }
            d.add(new Queue("q.LiberarBienSubasta.subasta"));
            return new Declarables(d);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    @Order(1)
    void timerAperturaAbreLaSubastaYEmiteSubastaIniciada() {
        jdbc.sql(
                        "UPDATE subasta SET fecha_inicio = now() - interval '1 minute',"
                                + " fecha_cierre = now() + interval '1 hour' WHERE id_subasta = 'SUB-0001'")
                .update();
        await().atMost(Duration.ofSeconds(10)).until(() -> estado("SUB-0001").equals("ABIERTA"));
        Message mensaje = rabbit.receive("q.subastaIniciada", 10_000);
        assertThat(mensaje).isNotNull();
        assertThat(new String(mensaje.getBody())).contains("\"idSubasta\":\"SUB-0001\"");
    }

    @Test
    @Order(2)
    void ofertaMenorAlMinimoDevuelve422() throws Exception {
        mvc.perform(oferta("SUB-0001", "cliente1", "1010101010", "1000"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.motivo").value("MENOR_AL_MINIMO"));
    }

    @Test
    @Order(3)
    void ofertaValidaSeRegistraYEmiteCambioOfertaMayor() throws Exception {
        mvc.perform(oferta("SUB-0001", "cliente1", "1010101010", "95000000"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(
                        jsonPath("$.mensaje")
                                .value(org.hamcrest.Matchers.containsString("celular")));
        Message mensaje = rabbit.receive("q.cambioOfertaMayor", 10_000);
        assertThat(mensaje).isNotNull();
        assertThat(new String(mensaje.getBody())).contains("\"valor\":95000000");
    }

    @Test
    @Order(4)
    void veinteOfertasSimultaneasNuncaDejanDosOfertasMayores() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(20);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String valor = String.valueOf(95_000_000 + i * 100_000);
            String cedula = "20000000" + String.format("%02d", i);
            Callable<Integer> tarea =
                    () -> {
                        salida.await();
                        return mvc.perform(oferta("SUB-0001", "c" + cedula, cedula, valor))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            resultados.add(hilos.submit(tarea));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> r : resultados) {
            estados.add(r.get());
        }
        hilos.shutdown();

        assertThat(estados).allMatch(s -> s == 202 || s == 409 || s == 422);
        assertThat(estados).contains(202);
        List<BigDecimal> aceptadas =
                jdbc.sql(
                                "SELECT valor FROM oferta WHERE id_subasta = 'SUB-0001' ORDER BY id_oferta")
                        .query(BigDecimal.class)
                        .list();
        for (int i = 1; i < aceptadas.size(); i++) {
            assertThat(aceptadas.get(i)).isGreaterThan(aceptadas.get(i - 1));
        }
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM oferta WHERE id_subasta = 'SUB-0001'"
                                                + " AND estado = 'REGISTRADA'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    @Order(5)
    void timerCierreAdjudicaAlMayorYEmiteSubastaCerrada() throws Exception {
        BigDecimal mayor =
                jdbc.sql("SELECT max(valor) FROM oferta WHERE id_subasta = 'SUB-0001'")
                        .query(BigDecimal.class)
                        .single();
        rabbit.execute(c -> c.queuePurge("q.subastaCerrada"));
        jdbc.sql(
                        "UPDATE subasta SET fecha_inicio = LEAST(fecha_inicio, now() - interval '2 minutes'),"
                                + " fecha_cierre = now() - interval '1 second'"
                                + " WHERE id_subasta IN ('SUB-0001', 'SUB-0002')")
                .update();

        await().atMost(Duration.ofSeconds(10)).until(() -> estado("SUB-0001").equals("ADJUDICADA"));
        assertThat(estado("SUB-0002")).isEqualTo("DESIERTA");
        assertThat(
                        jdbc.sql("SELECT valor_ganador FROM subasta WHERE id_subasta = 'SUB-0001'")
                                .query(BigDecimal.class)
                                .single())
                .isEqualByComparingTo(mayor);
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM oferta WHERE id_subasta = 'SUB-0001'"
                                                + " AND estado = 'GANADORA'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        List<String> cerradas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Message m = rabbit.receive("q.subastaCerrada", 10_000);
            assertThat(m).isNotNull();
            cerradas.add(new String(m.getBody()));
        }
        assertThat(cerradas)
                .anyMatch(c -> c.contains("SUB-0001") && c.contains("\"resultado\":\"ADJUDICADA\""))
                .anyMatch(c -> c.contains("SUB-0002") && c.contains("\"resultado\":\"DESIERTA\""));

        mvc.perform(oferta("SUB-0001", "cliente3", "1030303030", "999999999"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.motivo").value("SUBASTA_NO_ABIERTA"));
    }

    @Test
    @Order(6)
    void bienLiberadoLiberaLaSubastaAdjudicada() {
        String evento =
                "{\"idEvento\":\""
                        + UUID.randomUUID()
                        + "\",\"numeroCompra\":\"C-1\",\"idSubasta\":\"SUB-0001\",\"motivo\":\"pago rechazado\"}";
        rabbit.convertAndSend("", "q.LiberarBienSubasta.subasta", evento);
        await().atMost(Duration.ofSeconds(10)).until(() -> estado("SUB-0001").equals("LIBERADA"));
    }

    private String estado(String idSubasta) {
        return jdbc.sql("SELECT estado FROM subasta WHERE id_subasta = :id")
                .param("id", idSubasta)
                .query(String.class)
                .single();
    }

    private static org.springframework.test.web.servlet.RequestBuilder oferta(
            String idSubasta, String usuario, String cedula, String valor) {
        return post("/subastas/ofertas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"idSubasta\":\""
                                + idSubasta
                                + "\",\"cliente\":{\"usuario\":\""
                                + usuario
                                + "\",\"cedula\":\""
                                + cedula
                                + "\",\"celular\":\"300\",\"correo\":\"x@sso.local\"},\"valor\":"
                                + valor
                                + "}");
    }
}

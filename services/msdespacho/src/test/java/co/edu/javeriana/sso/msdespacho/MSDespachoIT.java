package co.edu.javeriana.sso.msdespacho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** VIII.3 – VIII.20 con PostgreSQL y RabbitMQ reales. */
@SpringBootTest(
        properties = {
            "DB_URL=jdbc:postgresql://reemplazado/x",
            "DB_USUARIO=x",
            "DB_CLAVE=x",
            "MOM_HOST=reemplazado",
            "MOM_USUARIO=x",
            "MOM_CLAVE=x",
            "sso.outbox.intervalo=200ms"
        })
@Testcontainers
@Import(MSDespachoIT.Colas.class)
class MSDespachoIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

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
                            "q.despachoSolicitud",
                            "q.inventarioSolicitud",
                            "q.envioSolicitud",
                            "q.inventarioConfirmacion",
                            "q.envioConfirmacion",
                            "q.despachoConfirmado")) {
                FanoutExchange exchange = new FanoutExchange(nombre);
                Queue cola = new Queue(nombre);
                d.addAll(List.of(exchange, cola, BindingBuilder.bind(cola).to(exchange)));
            }
            return new Declarables(d);
        }
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    void pideASinYSenYConfirmaUnaSolaVezCuandoAmbosResponden() throws Exception {
        rabbit.convertAndSend(
                "q.despachoSolicitud",
                "",
                "{\"idEvento\":\""
                        + id()
                        + "\",\"numeroCompra\":\"C-9\",\"idBien\":\"BIEN-9\",\"descripcion\":\"x\",\"direccion\":\"Cra 7\"}");
        assertThat(recibir("q.inventarioSolicitud")).contains("\"idBien\":\"BIEN-9\"");
        assertThat(recibir("q.envioSolicitud")).contains("\"direccion\":\"Cra 7\"");

        // Las dos confirmaciones llegan a la vez y repetidas
        String inv =
                "{\"idEvento\":\"" + id() + "\",\"numeroCompra\":\"C-9\",\"idBien\":\"BIEN-9\"}";
        String env =
                "{\"idEvento\":\""
                        + id()
                        + "\",\"numeroCompra\":\"C-9\",\"fechaProgramada\":\"2026-11-02\"}";
        Thread a =
                new Thread(
                        () -> {
                            rabbit.convertAndSend("q.inventarioConfirmacion", "", inv);
                            rabbit.convertAndSend("q.inventarioConfirmacion", "", inv);
                        });
        Thread b =
                new Thread(
                        () -> {
                            rabbit.convertAndSend("q.envioConfirmacion", "", env);
                            rabbit.convertAndSend("q.envioConfirmacion", "", env);
                        });
        a.start();
        b.start();
        a.join();
        b.join();

        assertThat(recibir("q.despachoConfirmado")).contains("\"numeroCompra\":\"C-9\"");
        assertThat(rabbit.receive("q.despachoConfirmado", 2000)).isNull();
        assertThat(
                        jdbc.sql("SELECT estado FROM despacho WHERE numero_compra = 'C-9'")
                                .query(String.class)
                                .single())
                .isEqualTo("CONFIRMADO");
    }

    @Test
    void conUnaSolaConfirmacionNoSeConfirmaElDespacho() {
        rabbit.convertAndSend(
                "q.despachoSolicitud",
                "",
                "{\"idEvento\":\""
                        + id()
                        + "\",\"numeroCompra\":\"C-10\",\"idBien\":\"BIEN-10\",\"descripcion\":\"x\",\"direccion\":\"Cra 7\"}");
        recibir("q.inventarioSolicitud");
        recibir("q.envioSolicitud");
        rabbit.convertAndSend(
                "q.inventarioConfirmacion",
                "",
                "{\"idEvento\":\"" + id() + "\",\"numeroCompra\":\"C-10\"}");
        await().atMost(Duration.ofSeconds(5))
                .until(
                        () ->
                                jdbc.sql(
                                                "SELECT inventario_confirmado FROM despacho WHERE numero_compra = 'C-10'")
                                        .query(Boolean.class)
                                        .single());
        assertThat(rabbit.receive("q.despachoConfirmado", 1500)).isNull();
    }

    private String recibir(String cola) {
        Message m = rabbit.receive(cola, 10_000);
        assertThat(m).as("mensaje en %s", cola).isNotNull();
        return new String(m.getBody(), StandardCharsets.UTF_8);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}

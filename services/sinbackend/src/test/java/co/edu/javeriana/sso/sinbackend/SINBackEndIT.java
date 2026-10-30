package co.edu.javeriana.sso.sinbackend;

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

/** VIII.9a – VIII.14a con PostgreSQL y RabbitMQ reales. */
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
@Import(SINBackEndIT.Colas.class)
class SINBackEndIT {

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
            for (String nombre : List.of("q.inventarioSolicitud", "q.inventarioConfirmacion")) {
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
    void registraLaSalidaUnaSolaVezYConfirma() {
        String solicitud =
                "{\"idEvento\":\"" + id() + "\",\"numeroCompra\":\"C-1\",\"idBien\":\"BIEN-1\"}";
        rabbit.convertAndSend("q.inventarioSolicitud", "", solicitud);
        rabbit.convertAndSend("q.inventarioSolicitud", "", solicitud);

        assertThat(recibir("q.inventarioConfirmacion")).contains("\"numeroCompra\":\"C-1\"");
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .until(
                        () ->
                                jdbc.sql("SELECT count(*) FROM salida_inventario")
                                                .query(Long.class)
                                                .single()
                                        == 1);
        assertThat(rabbit.receive("q.inventarioConfirmacion", 1500)).isNull();
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

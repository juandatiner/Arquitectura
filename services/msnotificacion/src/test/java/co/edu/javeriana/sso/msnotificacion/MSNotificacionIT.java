package co.edu.javeriana.sso.msnotificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * CU IX con DB NOTIFICACION (PostgreSQL), MOM (RabbitMQ) y CORREO (Mailpit) reales y una pasarela
 * SMS que falla la primera vez.
 */
@SpringBootTest(
        properties = {
            "DB_URL=jdbc:postgresql://reemplazado/x",
            "DB_USUARIO=x",
            "DB_CLAVE=x",
            "MOM_HOST=reemplazado",
            "MOM_USUARIO=x",
            "MOM_CLAVE=x",
            "MAIL_REMITENTE=notificaciones@sso.prueba",
            "spring.rabbitmq.listener.simple.retry.initial-interval=200ms"
        })
@Testcontainers
@Import(MSNotificacionIT.Colas.class)
class MSNotificacionIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

    @Container @ServiceConnection
    static final RabbitMQContainer MOM = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Container
    static final GenericContainer<?> CORREO =
            new GenericContainer<>("axllent/mailpit:v1.27").withExposedPorts(1025, 8025);

    static final AtomicInteger LLAMADAS_SMS = new AtomicInteger();
    static final HttpServer SMS;

    static {
        try {
            SMS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SMS.createContext(
                    "/sms",
                    x -> {
                        x.getRequestBody().readAllBytes();
                        if (LLAMADAS_SMS.incrementAndGet() == 1) {
                            x.sendResponseHeaders(503, -1);
                        } else {
                            byte[] r = "{\"id\":\"sms-1\"}".getBytes();
                            x.getResponseHeaders().add("Content-Type", "application/json");
                            x.sendResponseHeaders(202, r.length);
                            x.getResponseBody().write(r);
                        }
                        x.close();
                    });
            SMS.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void detener() {
        SMS.stop(0);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("MAIL_HOST", CORREO::getHost);
        registro.add("spring.mail.port", () -> CORREO.getMappedPort(1025));
        registro.add("RUTA_SMS", () -> "http://127.0.0.1:" + SMS.getAddress().getPort());
    }

    @TestConfiguration
    static class Colas {
        @Bean
        Queue notificacion() {
            return new Queue("q.notificacion");
        }
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    void enviaCorreoYSmsSinRepetirElCorreoCuandoElSmsFallaYSinReenviarDuplicados()
            throws Exception {
        String id = UUID.randomUUID().toString();
        String aviso =
                "{\"idEvento\":\""
                        + id
                        + "\",\"origen\":\"NotificacionCompraFinalizada\",\"numeroCompra\":\"C-1\","
                        + "\"destinatario\":{\"nombre\":\"Jorge\",\"correo\":\"jorge@prueba.co\",\"celular\":\"3159876543\"},"
                        + "\"asunto\":\"Compra C-1 realizada\",\"cuerpo\":\"Su compra C-1 fue realizada.\",\"sms\":true}";
        publicar(aviso);

        await().atMost(Duration.ofSeconds(15))
                .until(
                        () ->
                                jdbc.sql(
                                                        "SELECT count(*) FROM notificacion_enviada WHERE id_evento = CAST(:id AS UUID)")
                                                .param("id", id)
                                                .query(Long.class)
                                                .single()
                                        == 2);
        assertThat(LLAMADAS_SMS.get()).isEqualTo(2);
        assertThat(correos())
                .contains("\"total\":1")
                .contains("jorge@prueba.co")
                .contains("Compra C-1 realizada");

        publicar(aviso);
        Thread.sleep(1500);
        assertThat(correos()).contains("\"total\":1");
        assertThat(LLAMADAS_SMS.get()).isEqualTo(2);
    }

    private void publicar(String json) {
        rabbit.send(
                "",
                "q.notificacion",
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                        .setType("NotificacionCompraFinalizada")
                        .build());
    }

    private static String correos() throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://"
                                                        + CORREO.getHost()
                                                        + ":"
                                                        + CORREO.getMappedPort(8025)
                                                        + "/api/v1/messages"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
    }
}

package co.edu.javeriana.sso.mscompra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Saga completa con DB COMPRA (PostgreSQL) y MOM (RabbitMQ) reales y un WSValidacion simulado cuya
 * respuesta controla la prueba (APROBADO, RECHAZADO o caído).
 */
@SpringBootTest(
        properties = {
            "DB_URL=jdbc:postgresql://reemplazado-por-testcontainers/x",
            "DB_USUARIO=x",
            "DB_CLAVE=x",
            "MOM_HOST=reemplazado-por-testcontainers",
            "MOM_USUARIO=x",
            "MOM_CLAVE=x",
            "sso.compra.intervalo-validacion=300ms",
            "sso.compra.maximo-intentos-ofac=2",
            "sso.compra.espera-inicial-ofac=1s",
            "sso.outbox.intervalo=200ms"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import(MSCompraIT.Colas.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MSCompraIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer DB_COMPRA = new PostgreSQLContainer("postgres:17-alpine");

    @Container @ServiceConnection
    static final RabbitMQContainer MOM = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    /** Respuesta del WSValidacion simulado por cédula: APROBADO, RECHAZADO o CAIDO (503). */
    static final AtomicReference<String> OFAC = new AtomicReference<>("APROBADO");

    static final HttpServer VALIDACION;

    static {
        try {
            VALIDACION = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            VALIDACION.createContext(
                    "/validaciones",
                    x -> {
                        String cuerpo =
                                new String(
                                        x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String numero =
                                cuerpo.replaceAll(".*\"numeroCompra\":\"([^\"]+)\".*", "$1");
                        String estado = OFAC.get();
                        if (estado.equals("CAIDO")) {
                            x.sendResponseHeaders(503, -1);
                        } else {
                            byte[] r =
                                    ("{\"numeroCompra\":\""
                                                    + numero
                                                    + "\",\"estado\":\""
                                                    + estado
                                                    + "\"}")
                                            .getBytes();
                            x.getResponseHeaders().add("Content-Type", "application/json");
                            x.sendResponseHeaders(200, r.length);
                            x.getResponseBody().write(r);
                        }
                        x.close();
                    });
            VALIDACION.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void detener() {
        VALIDACION.stop(0);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add(
                "RUTA_MSVALIDACION", () -> "http://127.0.0.1:" + VALIDACION.getAddress().getPort());
    }

    /** Mismas colas que deploy/mom/definiciones/colas.json. */
    @TestConfiguration
    static class Colas {
        @Bean
        Declarables colas() {
            List<Declarable> d = new ArrayList<>();
            for (String nombre :
                    List.of(
                            "q.subastaCerrada",
                            "q.notificacion",
                            "q.pagoSolicitud",
                            "q.pagoConfirmado",
                            "q.despachoSolicitud",
                            "q.despachoConfirmado")) {
                FanoutExchange exchange = new FanoutExchange(nombre);
                Queue cola = new Queue(nombre);
                d.addAll(List.of(exchange, cola, BindingBuilder.bind(cola).to(exchange)));
            }
            FanoutExchange liberar = new FanoutExchange("q.LiberarBienSubasta");
            Queue colaLiberar = new Queue("q.LiberarBienSubasta.subasta");
            d.addAll(List.of(liberar, colaLiberar, BindingBuilder.bind(colaLiberar).to(liberar)));
            return new Declarables(d);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    @Order(1)
    void caminoFelizDeLaSubastaCerradaHastaLaCompraTerminada() throws Exception {
        OFAC.set("APROBADO");
        String cerrada = subastaCerrada("SUB-A", "1010101010");
        publicar("q.subastaCerrada", cerrada);
        publicar("q.subastaCerrada", cerrada);
        String numero = esperarCompra("SUB-A", "CREADA");
        assertThat(
                        jdbc.sql("SELECT count(*) FROM compra WHERE id_subasta = 'SUB-A'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(recibir("q.notificacion")).contains("NotificacionGanador").contains(numero);

        mvc.perform(iniciar(numero, "999")).andExpect(status().isForbidden());
        mvc.perform(iniciar(numero, "1010101010"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_VALIDACION"));
        mvc.perform(get("/compras").param("cedula", "1010101010"))
                .andExpect(jsonPath("$[0].numeroCompra").value(numero));

        esperarEstado(numero, "ESPERANDO_PAGO");
        assertThat(recibir("q.pagoSolicitud"))
                .contains(numero)
                .contains("\"valorOfertado\":150000000");

        publicar("q.pagoConfirmado", pago(numero, "APROBADO"));
        esperarEstado(numero, "PAGO_CONFIRMADO");
        assertThat(recibir("q.despachoSolicitud"))
                .contains(numero)
                .contains("\"direccion\":\"Cra 7 # 40-62\"");

        publicar(
                "q.despachoConfirmado",
                "{\"idEvento\":\"" + UUID.randomUUID() + "\",\"numeroCompra\":\"" + numero + "\"}");
        esperarEstado(numero, "TERMINADA");
        assertThat(recibir("q.notificacion"))
                .contains("NotificacionCompraFinalizada")
                .contains("\"sms\":true");
    }

    @Test
    @Order(2)
    void ofacRechazadoCancelaLaCompra() throws Exception {
        OFAC.set("RECHAZADO");
        publicar("q.subastaCerrada", subastaCerrada("SUB-B", "2020202020"));
        String numero = esperarCompra("SUB-B", "CREADA");
        recibir("q.notificacion");
        mvc.perform(iniciar(numero, "2020202020")).andExpect(status().isAccepted());
        esperarEstado(numero, "CANCELADA");
        assertThat(recibir("q.notificacion")).contains("NotificacionCancelacion");
    }

    @Test
    @Order(3)
    void ofacCaidoReintentaYAlAgotarIntentosQuedaEnRevision() throws Exception {
        OFAC.set("CAIDO");
        publicar("q.subastaCerrada", subastaCerrada("SUB-C", "3030303030"));
        String numero = esperarCompra("SUB-C", "CREADA");
        recibir("q.notificacion");
        mvc.perform(iniciar(numero, "3030303030")).andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                jdbc.sql(
                                                        "SELECT intentos_ofac FROM compra WHERE numero_compra = :n")
                                                .param("n", numero)
                                                .query(Integer.class)
                                                .single()
                                        >= 1);
        assertThat(estado(numero)).isEqualTo("EN_VALIDACION");
        esperarEstado(numero, "EN_REVISION");
        assertThat(recibir("q.notificacion")).contains("NotificacionDemora");
    }

    @Test
    @Order(4)
    void pagoRechazadoCompensaYLiberaElBien() throws Exception {
        OFAC.set("APROBADO");
        publicar("q.subastaCerrada", subastaCerrada("SUB-D", "4040404040"));
        String numero = esperarCompra("SUB-D", "CREADA");
        recibir("q.notificacion");
        mvc.perform(iniciar(numero, "4040404040")).andExpect(status().isAccepted());
        esperarEstado(numero, "ESPERANDO_PAGO");
        recibir("q.pagoSolicitud");

        publicar("q.pagoConfirmado", pago(numero, "RECHAZADO"));
        esperarEstado(numero, "CANCELADA");
        assertThat(recibir("q.LiberarBienSubasta.subasta"))
                .contains("\"idSubasta\":\"SUB-D\"")
                .contains("pago rechazado");
    }

    private String subastaCerrada(String idSubasta, String cedula) {
        return "{\"idEvento\":\""
                + UUID.randomUUID()
                + "\",\"idSubasta\":\""
                + idSubasta
                + "\",\"idBien\":\"BIEN-"
                + idSubasta
                + "\",\"descripcion\":\"Camioneta\",\"resultado\":\"ADJUDICADA\",\"ganador\":{\"usuario\":\"u"
                + cedula
                + "\",\"cedula\":\""
                + cedula
                + "\",\"nombre\":\"Cliente\",\"celular\":\"3001110001\",\"correo\":\"c@prueba.co\"},"
                + "\"valor\":150000000}";
    }

    private static String pago(String numero, String resultado) {
        return "{\"idEvento\":\""
                + UUID.randomUUID()
                + "\",\"numeroCompra\":\""
                + numero
                + "\",\"cedula\":\"x\",\"valorPagado\":178500000,\"referenciaPago\":\"YP-1\",\"resultado\":\""
                + resultado
                + "\"}";
    }

    private static org.springframework.test.web.servlet.RequestBuilder iniciar(
            String numero, String cedula) {
        return post("/compras/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"numeroCompra\":\""
                                + numero
                                + "\",\"cliente\":{\"usuario\":\"u\",\"cedula\":\""
                                + cedula
                                + "\",\"direccion\":\"Cra 7 # 40-62\"}}");
    }

    private void publicar(String cola, String json) {
        rabbit.convertAndSend(cola, "", json);
    }

    private String recibir(String cola) {
        Message m = rabbit.receive(cola, 10_000);
        assertThat(m).as("mensaje en %s", cola).isNotNull();
        return new String(m.getBody(), StandardCharsets.UTF_8)
                + " tipo="
                + m.getMessageProperties().getType();
    }

    private String esperarCompra(String idSubasta, String estado) {
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                jdbc.sql(
                                                        "SELECT count(*) FROM compra WHERE id_subasta = :s AND estado = :e")
                                                .param("s", idSubasta)
                                                .param("e", estado)
                                                .query(Long.class)
                                                .single()
                                        == 1);
        return jdbc.sql("SELECT numero_compra FROM compra WHERE id_subasta = :s")
                .param("s", idSubasta)
                .query(String.class)
                .single();
    }

    private void esperarEstado(String numero, String estado) {
        await().atMost(Duration.ofSeconds(20)).until(() -> estado(numero).equals(estado));
    }

    private String estado(String numero) {
        return jdbc.sql("SELECT estado FROM compra WHERE numero_compra = :n")
                .param("n", numero)
                .query(String.class)
                .single();
    }
}

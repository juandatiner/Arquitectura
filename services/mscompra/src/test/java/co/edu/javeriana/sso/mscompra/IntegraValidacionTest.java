package co.edu.javeriana.sso.mscompra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import co.edu.javeriana.sso.mscompra.config.PropiedadesCompra;
import co.edu.javeriana.sso.mscompra.modelo.EstadoOFAC;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IntegraValidacionTest {

    @Test
    void elCuerpoLlevaExactamenteLosParametrosDelPasoEnElMismoOrden() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer validacion = MockRestServiceServer.bindTo(builder).build();
        validacion
                .expect(requestTo("http://msvalidacion/validaciones"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{\"numeroCompra\":\"C-1\",\"cedula\":\"1010\"}"))
                .andRespond(
                        withSuccess(
                                "{\"numeroCompra\":\"C-1\",\"estado\":\"APROBADO\"}",
                                MediaType.APPLICATION_JSON));
        IntegraValidacion integra =
                new IntegraValidacion(
                        builder.build(),
                        CircuitBreaker.ofDefaults("validacion"),
                        new PropiedadesCompra(
                                "http://msvalidacion",
                                20,
                                Duration.ofSeconds(60),
                                3,
                                Duration.ofSeconds(10),
                                Duration.ofMinutes(5),
                                Duration.ofMinutes(30),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1)));

        assertThat(integra.validar("C-1", "1010")).isEqualTo(EstadoOFAC.APROBADO);

        validacion.verify();
    }
}

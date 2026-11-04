package co.edu.javeriana.sso.msvalidacion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TrazabilidadTest {

    private static final String ID = "abc-12345";

    private final AtomicReference<String> visto = new AtomicReference<>();

    private final MockFilterChain cadena =
            new MockFilterChain(
                    new jakarta.servlet.http.HttpServlet() {
                        @Override
                        public void service(
                                jakarta.servlet.ServletRequest p,
                                jakarta.servlet.ServletResponse r) {
                            visto.set(MDC.get(Correlacion.CLAVE_LOG));
                        }
                    });

    private MockHttpServletResponse filtrar(String recibido) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/x");
        if (recibido != null) {
            peticion.addHeader(Correlacion.CABECERA_HTTP, recibido);
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new FiltroCorrelacion().doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    @Test
    void conservaElCorrelationIdRecibidoYLoLimpiaAlTerminar() throws Exception {
        MockHttpServletResponse respuesta = filtrar(ID);

        assertThat(visto).hasValue(ID);
        assertThat(respuesta.getHeader(Correlacion.CABECERA_HTTP)).isEqualTo(ID);
        assertThat(MDC.get(Correlacion.CLAVE_LOG)).isNull();
    }

    @Test
    void creaUnoSiNoLlega() throws Exception {
        MockHttpServletResponse respuesta = filtrar(null);

        assertThat(visto.get()).isNotBlank();
        assertThat(respuesta.getHeader(Correlacion.CABECERA_HTTP)).isEqualTo(visto.get());
    }

    @Test
    void reemplazaElQueNoEsValidoParaNoInyectarTextoEnLosLogs() throws Exception {
        filtrar("x\"}\n{\"falso\":\"inyectado");

        assertThat(visto.get()).doesNotContain("falso").hasSize(36);
    }

    @Test
    void lasLlamadasHttpSalientesLlevanElCorrelationIdEnCurso() {
        RestClient.Builder constructor = RestClient.builder();
        new ConfiguracionTrazabilidad().propagarCorrelacionHttp().customize(constructor);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(header(Correlacion.CABECERA_HTTP, ID)).andRespond(withSuccess());

        try (MDC.MDCCloseable ambito = Correlacion.abrir(ID)) {
            constructor.build().get().uri("http://destino/x").retrieve().toBodilessEntity();
        }

        servidor.verify();
    }
}

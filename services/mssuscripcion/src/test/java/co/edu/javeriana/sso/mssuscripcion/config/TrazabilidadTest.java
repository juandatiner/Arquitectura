package co.edu.javeriana.sso.mssuscripcion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
    void cadaEjecucionProgramadaTieneSuPropioCorrelationId() {
        AtomicReference<String> visto = new AtomicReference<>();
        Runnable tarea =
                new ConfiguracionTrazabilidad()
                        .correlacionPorEjecucion()
                        .decorate(() -> visto.set(MDC.get(Correlacion.CLAVE_LOG)));

        tarea.run();
        String primero = visto.get();
        tarea.run();

        assertThat(primero).isNotBlank().isNotEqualTo(visto.get());
        assertThat(MDC.get(Correlacion.CLAVE_LOG)).isNull();
    }

    @Test
    void elListenerProcesaElMensajeConElCorrelationIdDeSuCabecera() throws Throwable {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setHeader(Correlacion.CABECERA_AMQP, ID);
        AtomicReference<String> visto = new AtomicReference<>();
        MethodInvocation invocacion = mock(MethodInvocation.class);
        when(invocacion.getArguments())
                .thenReturn(new Object[] {null, new Message(new byte[0], propiedades)});
        when(invocacion.proceed())
                .thenAnswer(
                        i -> {
                            visto.set(MDC.get(Correlacion.CLAVE_LOG));
                            return null;
                        });

        ConfiguracionTrazabilidad.procesarMensaje(invocacion);

        assertThat(visto).hasValue(ID);
        assertThat(MDC.get(Correlacion.CLAVE_LOG)).isNull();
    }

    @Test
    void laCorrelacionVaAntesDeLosReintentosDelListener() {
        Advice reintentos = mock(Advice.class);
        SimpleRabbitListenerContainerFactory fabrica = new SimpleRabbitListenerContainerFactory();
        fabrica.setAdviceChain(reintentos);

        ConfiguracionTrazabilidad.correlacionEnListeners()
                .postProcessAfterInitialization(fabrica, "fabrica");

        assertThat(fabrica.getAdviceChain()).hasSize(2).endsWith(reintentos);
    }
}

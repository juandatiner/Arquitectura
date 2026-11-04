package co.edu.javeriana.sso.apigateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Toma el correlationId de la petición (o crea uno) y lo devuelve en la respuesta. Registra la
 * plantilla de la ruta, no la URL: así la cédula de una ruta o una consulta no llega a los logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FiltroCorrelacion.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String correlationId =
                Correlacion.normalizar(peticion.getHeader(Correlacion.CABECERA_HTTP));
        respuesta.setHeader(Correlacion.CABECERA_HTTP, correlationId);
        long inicio = System.nanoTime();
        try (MDC.MDCCloseable ambito = Correlacion.abrir(correlationId)) {
            try {
                cadena.doFilter(peticion, respuesta);
            } finally {
                if (!peticion.getRequestURI().startsWith("/actuator")) {
                    Object ruta =
                            peticion.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    LOG.atInfo()
                            .addKeyValue("metodo", peticion.getMethod())
                            .addKeyValue("ruta", ruta == null ? "sin-ruta" : ruta)
                            .addKeyValue("estado", respuesta.getStatus())
                            .addKeyValue("duracionMs", (System.nanoTime() - inicio) / 1_000_000)
                            .log("peticion atendida");
                }
            }
        }
    }
}

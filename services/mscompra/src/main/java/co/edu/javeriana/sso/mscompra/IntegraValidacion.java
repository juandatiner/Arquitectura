package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.config.PropiedadesCompra;
import co.edu.javeriana.sso.mscompra.modelo.EstadoOFAC;
import co.edu.javeriana.sso.mscompra.modelo.ResultadoValidacion;
import co.edu.javeriana.sso.mscompra.modelo.ValidacionNoDisponibleException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Integración con MSValidacion (WSValidacion) por HTTP/REST. Tolerante a caídas de red y de OFAC:
 * tiempo máximo por llamada y circuit breaker; si no hay respuesta, la saga reintenta más tarde.
 */
@Component
public class IntegraValidacion {

    private final RestClient http;
    private final CircuitBreaker circuito;
    private final String urlValidacion;

    public IntegraValidacion(
            RestClient http, CircuitBreaker circuitoValidacion, PropiedadesCompra propiedades) {
        this.http = http;
        this.circuito = circuitoValidacion;
        this.urlValidacion = propiedades.urlValidacion();
    }

    /**
     * Pasos VI.13 (SrvCompraSaga → IntegraValidacion) y VI.14 (→ WSValidacion validar, HTTP/REST).
     */
    public EstadoOFAC validar(String numeroCompra, String cedula) {
        try {
            ResultadoValidacion resultado =
                    circuito.executeSupplier(
                            () ->
                                    http.post()
                                            .uri(urlValidacion + "/validaciones")
                                            .body(
                                                    Map.of(
                                                            "numeroCompra",
                                                            numeroCompra,
                                                            "cedula",
                                                            cedula))
                                            .retrieve()
                                            .body(ResultadoValidacion.class));
            if (resultado == null || resultado.estado() == null) {
                throw new ValidacionNoDisponibleException("Respuesta vacía de MSValidacion", null);
            }
            return resultado.estado();
        } catch (CallNotPermittedException e) {
            throw new ValidacionNoDisponibleException("Circuito abierto hacia MSValidacion", e);
        } catch (RestClientException e) {
            throw new ValidacionNoDisponibleException(
                    "MSValidacion no responde: " + e.getMessage(), e);
        }
    }
}

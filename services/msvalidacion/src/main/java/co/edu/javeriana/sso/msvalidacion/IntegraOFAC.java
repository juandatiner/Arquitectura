package co.edu.javeriana.sso.msvalidacion;

import co.edu.javeriana.sso.msvalidacion.config.PropiedadesValidacion;
import co.edu.javeriana.sso.msvalidacion.modelo.EstadoOFAC;
import co.edu.javeriana.sso.msvalidacion.modelo.OfacNoDisponibleException;
import co.edu.javeriana.sso.msvalidacion.modelo.RespuestaOFAC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Integración con la OFAC (sistema externo, OFAC SERVER :9001) por HTTP/REST, con tiempo máximo.
 */
@Component
public class IntegraOFAC {

    private final RestClient http;
    private final String urlOfac;

    public IntegraOFAC(RestClient http, PropiedadesValidacion propiedades) {
        this.http = http;
        this.urlOfac = propiedades.urlOfac();
    }

    /**
     * Pasos VI.17 (SrvValidarOFAC → IntegraOFAC consultar(cedula)) y VI.18 (→ WSOfac
     * validarCedula).
     */
    public EstadoOFAC consultar(String cedula) {
        try {
            RespuestaOFAC respuesta =
                    http.get()
                            .uri(urlOfac + "/ofac/{cedula}", cedula)
                            .retrieve()
                            .body(RespuestaOFAC.class);
            if (respuesta == null || respuesta.estado() == null) {
                throw new OfacNoDisponibleException(null);
            }
            return respuesta.estado();
        } catch (RestClientException e) {
            throw new OfacNoDisponibleException(e);
        }
    }
}

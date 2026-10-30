package co.edu.javeriana.sso.msnotificacion;

import co.edu.javeriana.sso.msnotificacion.config.PropiedadesNotificacion;
import co.edu.javeriana.sso.msnotificacion.modelo.EnvioFallidoException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Integración con la pasarela de SMS (SMS SERVER :9004) por HTTP. */
@Component
public class IntegraSMS {

    private final RestClient http;
    private final String urlSms;

    public IntegraSMS(RestClient http, PropiedadesNotificacion propiedades) {
        this.http = http;
        this.urlSms = propiedades.urlSms();
    }

    /**
     * Pasos IX.6 (SrvNotificar → IntegraSMS enviarSMS(numero, texto)) e IX.7 (→ SMS GATEWAY enviar,
     * HTTP).
     */
    public String enviarSMS(String numero, String texto) {
        try {
            Map<?, ?> respuesta =
                    http.post()
                            .uri(urlSms + "/sms")
                            .body(Map.of("numero", numero, "texto", texto))
                            .retrieve()
                            .body(Map.class);
            return "ENVIADO " + (respuesta == null ? "" : respuesta.get("id"));
        } catch (RestClientException e) {
            throw new EnvioFallidoException("No se pudo enviar el SMS a " + numero, e);
        }
    }
}

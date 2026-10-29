package co.edu.javeriana.sso.apigateway;

import co.edu.javeriana.sso.apigateway.config.PropiedadesGateway;
import co.edu.javeriana.sso.apigateway.modelo.Filtros;
import co.edu.javeriana.sso.apigateway.modelo.IdentidadCliente;
import co.edu.javeriana.sso.apigateway.modelo.ServicioNoDisponibleException;
import co.edu.javeriana.sso.apigateway.modelo.Solicitud;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Enruta cada petición autorizada al componente interno correspondiente, por las flechas HTTP del
 * diagrama: WSAcceso, WSCatalogo, WSSuscripcion, WSSubasta, Nginx (upstream msCompra) y
 * WSPagoYoPago. Devuelve la respuesta del componente sin modificarla.
 */
@Service
public class SrvRuteo {

    private final RestClient http;
    private final PropiedadesGateway.Rutas rutas;

    public SrvRuteo(RestClient http, PropiedadesGateway propiedades) {
        this.http = http;
        this.rutas = propiedades.rutas();
    }

    /** Paso I.7 (SrvRuteo → WSAcceso autenticar(usuario, clave)). */
    public ResponseEntity<String> enrutarLogin(String usuario, String clave) {
        return enviar(
                "MSAcceso",
                HttpMethod.POST,
                URI.create(rutas.msAcceso() + "/acceso/login"),
                cuerpo("usuario", usuario, "clave", clave));
    }

    /** Paso II.7a (SrvRuteo → WSCatalogo consultarCatalogo(filtros)). */
    public ResponseEntity<String> enrutarConsultaCatalogo(
            Filtros filtros, IdentidadCliente cliente) {
        URI uri =
                UriComponentsBuilder.fromUriString(rutas.msCatalogo() + "/catalogo")
                        .queryParamIfPresent(
                                "texto", java.util.Optional.ofNullable(filtros.texto()))
                        .queryParamIfPresent(
                                "categoria", java.util.Optional.ofNullable(filtros.categoria()))
                        .queryParamIfPresent(
                                "pagina", java.util.Optional.ofNullable(filtros.pagina()))
                        .queryParamIfPresent(
                                "tamano", java.util.Optional.ofNullable(filtros.tamano()))
                        .encode()
                        .build()
                        .toUri();
        return enviar("MSCatalogo", HttpMethod.GET, uri, null);
    }

    /** Paso II.7b (SrvRuteo → WSSuscripcion suscribirse / cancelarSuscripcion). */
    public ResponseEntity<String> enrutarSuscripcion(
            Solicitud solicitud, IdentidadCliente cliente) {
        return switch (solicitud) {
            case Solicitud.Suscripcion s ->
                    enviar(
                            "MSSuscripcion",
                            HttpMethod.POST,
                            URI.create(rutas.msSuscripcion() + "/suscripciones"),
                            cuerpo("criterio", s.criterio(), "cliente", cliente));
            case Solicitud.CancelacionSuscripcion s ->
                    enviar(
                            "MSSuscripcion",
                            HttpMethod.POST,
                            URI.create(rutas.msSuscripcion() + "/suscripciones/cancelar"),
                            cuerpo("idSuscripcion", s.idSuscripcion(), "cliente", cliente));
            default -> throw new IllegalArgumentException("No es una solicitud de suscripción");
        };
    }

    /** Paso III.7 (SrvRuteo → WSCatalogo publicarNuevaSubasta(idSubasta, admin)). */
    public ResponseEntity<String> enrutarPublicacion(String idSubasta, IdentidadCliente admin) {
        return enviar(
                "MSCatalogo",
                HttpMethod.POST,
                URI.create(rutas.msCatalogo() + "/catalogo/publicar"),
                cuerpo("idSubasta", idSubasta, "admin", admin));
    }

    /** Paso IV.7 (SrvRuteo → WSSubasta registrarOferta(idSubasta, cliente, valor)). */
    public ResponseEntity<String> enrutarOferta(
            String idSubasta, IdentidadCliente cliente, BigDecimal valor) {
        return enviar(
                "MSSubasta",
                HttpMethod.POST,
                URI.create(rutas.msSubasta() + "/subastas/ofertas"),
                cuerpo("idSubasta", idSubasta, "cliente", cliente, "valor", valor));
    }

    /** Paso VI.7 (SrvRuteo → Nginx iniciarCompra, upstream msCompra least_conn) → VI.8 WSCompra. */
    public ResponseEntity<String> enrutarInicioCompra(
            String numeroCompra, IdentidadCliente cliente) {
        return enviar(
                "MSCompra",
                HttpMethod.POST,
                URI.create(rutas.balanceadorCompra() + "/compras/iniciar"),
                cuerpo("numeroCompra", numeroCompra, "cliente", cliente));
    }

    /** VI.1 consultarMisCompras por la flecha SrvRuteo → Nginx → WSCompra (observación 6). */
    public ResponseEntity<String> enrutarConsultaCompras(IdentidadCliente cliente) {
        URI uri =
                UriComponentsBuilder.fromUriString(rutas.balanceadorCompra() + "/compras")
                        .queryParam("cedula", cliente.cedula())
                        .encode()
                        .build()
                        .toUri();
        return enviar("MSCompra", HttpMethod.GET, uri, null);
    }

    /** Paso VII.24 (SrvRuteo → WSPagoYoPago reportarPago(cedula, numeroCompra, valorPagado)). */
    public ResponseEntity<String> enrutarReportePago(
            String cedula, String numeroCompra, BigDecimal valorPagado) {
        return enviar(
                "MSPago",
                HttpMethod.POST,
                URI.create(rutas.msPago() + "/pagos/reporte"),
                cuerpo("cedula", cedula, "numeroCompra", numeroCompra, "valorPagado", valorPagado));
    }

    private ResponseEntity<String> enviar(
            String destino, HttpMethod metodo, URI uri, Map<String, Object> cuerpo) {
        RestClient.RequestBodySpec peticion =
                http.method(metodo).uri(uri).accept(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
        }
        try {
            return peticion.exchange(
                    (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        HttpHeaders cabeceras = new HttpHeaders();
                        MediaType tipo = res.getHeaders().getContentType();
                        if (tipo != null) {
                            cabeceras.setContentType(tipo);
                        }
                        return ResponseEntity.status(res.getStatusCode())
                                .headers(cabeceras)
                                .body(body);
                    });
        } catch (ResourceAccessException e) {
            throw new ServicioNoDisponibleException(destino, e);
        }
    }

    /** Cuerpo JSON con los parámetros del paso, en el mismo orden. */
    private static Map<String, Object> cuerpo(Object... claveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put((String) claveValor[i], claveValor[i + 1]);
        }
        return mapa;
    }
}

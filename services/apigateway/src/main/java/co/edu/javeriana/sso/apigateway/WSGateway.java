package co.edu.javeriana.sso.apigateway;

import co.edu.javeriana.sso.apigateway.modelo.Credenciales;
import co.edu.javeriana.sso.apigateway.modelo.CriterioSuscripcion;
import co.edu.javeriana.sso.apigateway.modelo.Filtros;
import co.edu.javeriana.sso.apigateway.modelo.Oferta;
import co.edu.javeriana.sso.apigateway.modelo.Publicacion;
import co.edu.javeriana.sso.apigateway.modelo.ReportePago;
import co.edu.javeriana.sso.apigateway.modelo.RutaLogin;
import co.edu.javeriana.sso.apigateway.modelo.Solicitud;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único punto de entrada de la DMZ. Lo invoca Nginx (BALANCEADOR DE CARGAS :80). No contiene lógica
 * de negocio: todo pasa por SrvSeguridadToken, que a su vez llama a SrvRuteo.
 */
@RestController
@RequestMapping("/api")
public class WSGateway {

    private static final String RUTA_LOGIN = "/api/acceso/login";

    private final SrvSeguridadToken srvSeguridadToken;

    public WSGateway(SrvSeguridadToken srvSeguridadToken) {
        this.srvSeguridadToken = srvSeguridadToken;
    }

    /** Pasos I.4 (Nginx → WSGateway) e I.5 (→ SrvSeguridadToken evaluarAcceso). */
    @PostMapping("/acceso/login")
    public ResponseEntity<String> autenticar(@Valid @RequestBody Credenciales credenciales) {
        return srvSeguridadToken.evaluarAcceso(new RutaLogin(RUTA_LOGIN, credenciales));
    }

    /** Pasos II.4 (Nginx → WSGateway, GET /api/catalogo) e II.5 (→ validarYAutorizar). */
    @GetMapping("/catalogo")
    public ResponseEntity<String> consultarCatalogo(
            Filtros filtros,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return srvSeguridadToken.validarYAutorizar(new Solicitud.ConsultaCatalogo(filtros, token));
    }

    /** Pasos II.4 (Nginx → WSGateway, POST /api/suscripciones) e II.5. */
    @PostMapping("/suscripciones")
    public ResponseEntity<String> suscribirse(
            @Valid @RequestBody CriterioSuscripcion criterio,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return srvSeguridadToken.validarYAutorizar(new Solicitud.Suscripcion(criterio, token));
    }

    /** Pasos II.4 e II.5, camino b de II.7b (cancelarSuscripcion). */
    @DeleteMapping("/suscripciones/{idSuscripcion}")
    public ResponseEntity<String> cancelarSuscripcion(
            @PathVariable String idSuscripcion,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return srvSeguridadToken.validarYAutorizar(
                new Solicitud.CancelacionSuscripcion(idSuscripcion, token));
    }

    /** Pasos III.4 (Nginx → WSGateway, POST /api/catalogo/publicar) e III.5. */
    @PostMapping("/catalogo/publicar")
    public ResponseEntity<String> publicarNuevaSubasta(
            @Valid @RequestBody Publicacion publicacion,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return srvSeguridadToken.validarYAutorizar(
                new Solicitud.Publicacion(publicacion.idSubasta(), token));
    }

    /** Pasos IV.4 (Nginx → WSGateway, POST /api/subastas/{id}/ofertas) e IV.5. */
    @PostMapping("/subastas/{idSubasta}/ofertas")
    public ResponseEntity<String> registrarOferta(
            @PathVariable String idSubasta,
            @Valid @RequestBody Oferta oferta,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        BigDecimal valor = oferta.valor();
        return srvSeguridadToken.validarYAutorizar(new Solicitud.Oferta(idSubasta, valor, token));
    }

    /** VI.1 consultarMisCompras sobre las flechas de VI.4 – VI.8 (observación 6). */
    @GetMapping("/compras")
    public ResponseEntity<String> consultarMisCompras(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return srvSeguridadToken.validarYAutorizar(new Solicitud.ConsultaCompras(token));
    }

    /** Pasos VI.4 (Nginx → WSGateway, POST /api/compras/{numero}/iniciar) y VI.5. */
    @PostMapping("/compras/{numeroCompra}/iniciar")
    public ResponseEntity<String> iniciarCompra(
            @PathVariable String numeroCompra,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token) {
        return srvSeguridadToken.validarYAutorizar(new Solicitud.InicioCompra(numeroCompra, token));
    }

    /** Pasos VII.21 (Nginx → WSGateway, POST /api/pagos/reporte desde YoPago) y VII.22. */
    @PostMapping("/pagos/reporte")
    public ResponseEntity<String> reportarPago(@Valid @RequestBody ReportePago reporte) {
        return srvSeguridadToken.validarYAutorizar(
                new Solicitud.ReportePago(
                        reporte.cedula(),
                        reporte.numeroCompra(),
                        reporte.valorPagado(),
                        reporte.credencial()));
    }
}

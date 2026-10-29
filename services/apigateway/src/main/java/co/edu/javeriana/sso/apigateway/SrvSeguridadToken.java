package co.edu.javeriana.sso.apigateway;

import co.edu.javeriana.sso.apigateway.config.PropiedadesGateway;
import co.edu.javeriana.sso.apigateway.modelo.AccesoDenegadoException;
import co.edu.javeriana.sso.apigateway.modelo.IdentidadCliente;
import co.edu.javeriana.sso.apigateway.modelo.RutaLogin;
import co.edu.javeriana.sso.apigateway.modelo.Solicitud;
import co.edu.javeriana.sso.apigateway.modelo.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Date;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Seguridad de la DMZ: decide si una petición puede entrar a la red interna y, si puede, la entrega
 * a SrvRuteo con la identidad del cliente tomada del token (nunca del cuerpo de la petición).
 */
@Service
public class SrvSeguridadToken {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final SrvRuteo srvRuteo;
    private final PublicKey clavePublicaToken;
    private final PropiedadesGateway propiedades;
    private final Clock reloj;

    public SrvSeguridadToken(
            SrvRuteo srvRuteo,
            PublicKey clavePublicaToken,
            PropiedadesGateway propiedades,
            Clock reloj) {
        this.srvRuteo = srvRuteo;
        this.clavePublicaToken = clavePublicaToken;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Pasos I.5 (WSGateway → evaluarAcceso) e I.6 (→ SrvRuteo enrutarLogin): ruta pública. */
    public ResponseEntity<String> evaluarAcceso(RutaLogin rutaLogin) {
        return srvRuteo.enrutarLogin(
                rutaLogin.credenciales().usuario(), rutaLogin.credenciales().clave());
    }

    /** Pasos II.5/II.6, III.5/III.6, IV.5/IV.6, VI.5/VI.6 y VII.22/VII.23. */
    public ResponseEntity<String> validarYAutorizar(Solicitud solicitud) {
        return switch (solicitud) {
            case Solicitud.ConsultaCatalogo s ->
                    srvRuteo.enrutarConsultaCatalogo(s.filtros(), verificarToken(s.token()));
            case Solicitud.Suscripcion s ->
                    srvRuteo.enrutarSuscripcion(s, verificarToken(s.token()));
            case Solicitud.CancelacionSuscripcion s ->
                    srvRuteo.enrutarSuscripcion(s, verificarToken(s.token()));
            case Solicitud.Publicacion s -> {
                IdentidadCliente admin = verificarToken(s.token());
                if (!admin.esAdmin()) {
                    throw new AccesoDenegadoException(
                            "Solo un administrador puede publicar subastas");
                }
                yield srvRuteo.enrutarPublicacion(s.idSubasta(), admin);
            }
            case Solicitud.Oferta s ->
                    srvRuteo.enrutarOferta(s.idSubasta(), verificarToken(s.token()), s.valor());
            case Solicitud.InicioCompra s ->
                    srvRuteo.enrutarInicioCompra(s.numeroCompra(), verificarToken(s.token()));
            case Solicitud.ConsultaCompras s ->
                    srvRuteo.enrutarConsultaCompras(verificarToken(s.token()));
            case Solicitud.ReportePago s -> {
                verificarCredencialYoPago(s.credencial());
                yield srvRuteo.enrutarReportePago(s.cedula(), s.numeroCompra(), s.valorPagado());
            }
        };
    }

    /** Firma RS256 de MSAcceso, emisor y vigencia. */
    private IdentidadCliente verificarToken(String token) {
        if (token == null || !token.startsWith(PREFIJO_BEARER)) {
            throw new TokenInvalidoException("Debe iniciar sesión");
        }
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(clavePublicaToken)
                            .requireIssuer(propiedades.token().emisor())
                            .clock(() -> Date.from(reloj.instant()))
                            .build()
                            .parseSignedClaims(token.substring(PREFIJO_BEARER.length()))
                            .getPayload();
            return new IdentidadCliente(
                    claims.getSubject(),
                    claims.get("cedula", String.class),
                    claims.get("nombre", String.class),
                    claims.get("celular", String.class),
                    claims.get("correo", String.class),
                    claims.get("rol", String.class));
        } catch (ExpiredJwtException e) {
            throw new TokenInvalidoException("La sesión expiró, inicie sesión de nuevo");
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException("Token inválido");
        }
    }

    /** Credencial de sistema de YoPago, comparada en tiempo constante. */
    private void verificarCredencialYoPago(String credencial) {
        byte[] esperada = propiedades.credencialYoPago().getBytes(StandardCharsets.UTF_8);
        byte[] recibida = credencial.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperada, recibida)) {
            throw new AccesoDenegadoException("Credencial de YoPago inválida");
        }
    }
}

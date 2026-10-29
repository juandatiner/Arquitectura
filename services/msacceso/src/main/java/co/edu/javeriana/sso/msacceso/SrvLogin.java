package co.edu.javeriana.sso.msacceso;

import co.edu.javeriana.sso.msacceso.config.PropiedadesToken;
import co.edu.javeriana.sso.msacceso.modelo.DatosCliente;
import co.edu.javeriana.sso.msacceso.modelo.RespuestaAcceso;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

/** Lógica de autenticación: valida contra AD, obtiene los datos del cliente y emite el token. */
@Service
public class SrvLogin {

    private final IntegraLoginAD integraLoginAD;
    private final RepoLogin repoLogin;
    private final PropiedadesToken propiedadesToken;
    private final Clock reloj;

    public SrvLogin(
            IntegraLoginAD integraLoginAD,
            RepoLogin repoLogin,
            PropiedadesToken propiedadesToken,
            Clock reloj) {
        this.integraLoginAD = integraLoginAD;
        this.repoLogin = repoLogin;
        this.propiedadesToken = propiedadesToken;
        this.reloj = reloj;
    }

    /** Pasos I.8 (WSAcceso → SrvLogin), I.9 (→ IntegraLoginAD) e I.11 (→ RepoLogin). */
    public RespuestaAcceso autenticar(String usuario, String clave) {
        String identidadValidada = integraLoginAD.validarCredenciales(usuario, clave);
        DatosCliente cliente = repoLogin.obtenerDatosCliente(identidadValidada);
        Instant ahora = Instant.now(reloj);
        Instant expira = ahora.plus(propiedadesToken.duracion());
        String token =
                Jwts.builder()
                        .issuer(propiedadesToken.emisor())
                        .subject(cliente.usuario())
                        .claim("cedula", cliente.cedula())
                        .claim("nombre", cliente.nombre())
                        .claim("celular", cliente.celular())
                        .claim("correo", cliente.correo())
                        .claim("rol", cliente.rol().name())
                        .issuedAt(Date.from(ahora))
                        .expiration(Date.from(expira))
                        .signWith(propiedadesToken.clavePrivada())
                        .compact();
        return new RespuestaAcceso(token, expira, cliente);
    }
}

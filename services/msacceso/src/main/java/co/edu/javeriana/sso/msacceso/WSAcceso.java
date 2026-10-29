package co.edu.javeriana.sso.msacceso;

import co.edu.javeriana.sso.msacceso.modelo.Credenciales;
import co.edu.javeriana.sso.msacceso.modelo.RespuestaAcceso;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servicio web de acceso. Lo invoca SrvRuteo (APIGateway) por HTTP. */
@RestController
@RequestMapping("/acceso")
public class WSAcceso {

    private final SrvLogin srvLogin;

    public WSAcceso(SrvLogin srvLogin) {
        this.srvLogin = srvLogin;
    }

    /** Pasos I.7 (SrvRuteo → WSAcceso) y I.8 (WSAcceso → SrvLogin). */
    @PostMapping("/login")
    public RespuestaAcceso autenticar(@Valid @RequestBody Credenciales credenciales) {
        return srvLogin.autenticar(credenciales.usuario(), credenciales.clave());
    }
}

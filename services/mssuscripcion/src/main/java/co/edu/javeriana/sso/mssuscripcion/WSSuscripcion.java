package co.edu.javeriana.sso.mssuscripcion;

import co.edu.javeriana.sso.mssuscripcion.modelo.SolicitudCancelacion;
import co.edu.javeriana.sso.mssuscripcion.modelo.SolicitudSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.Suscripcion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Servicio web de suscripciones. Lo invoca SrvRuteo (APIGateway) por HTTP. */
@RestController
@RequestMapping("/suscripciones")
public class WSSuscripcion {

    private final SrvSuscripcion srvSuscripcion;

    public WSSuscripcion(SrvSuscripcion srvSuscripcion) {
        this.srvSuscripcion = srvSuscripcion;
    }

    /** Pasos II.7b (SrvRuteo → WSSuscripcion suscribirse) e II.8b (→ SrvSuscripcion). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Suscripcion suscribirse(@Valid @RequestBody SolicitudSuscripcion solicitud) {
        return srvSuscripcion.suscribirse(solicitud.criterio(), solicitud.cliente());
    }

    /** Pasos II.7b (SrvRuteo → WSSuscripcion cancelarSuscripcion) e II.8b (→ SrvSuscripcion). */
    @PostMapping("/cancelar")
    public Suscripcion cancelarSuscripcion(@Valid @RequestBody SolicitudCancelacion solicitud) {
        return srvSuscripcion.cancelarSuscripcion(solicitud.idSuscripcion(), solicitud.cliente());
    }
}

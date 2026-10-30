package co.edu.javeriana.sso.msvalidacion;

import co.edu.javeriana.sso.msvalidacion.modelo.ResultadoValidacion;
import co.edu.javeriana.sso.msvalidacion.modelo.SolicitudValidacion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Servicio web de validación. Lo invoca IntegraValidacion (MSCompra, réplicas 1 y 2) por HTTP/REST.
 */
@RestController
@RequestMapping("/validaciones")
public class WSValidacion {

    private final SrvValidarOFAC srvValidarOFAC;

    public WSValidacion(SrvValidarOFAC srvValidarOFAC) {
        this.srvValidarOFAC = srvValidarOFAC;
    }

    /**
     * Pasos VI.14 (IntegraValidacion → WSValidacion validar(numeroCompra, cedula)) y VI.15 (→
     * SrvValidarOFAC).
     */
    @PostMapping
    public ResultadoValidacion validar(@Valid @RequestBody SolicitudValidacion solicitud) {
        return new ResultadoValidacion(
                solicitud.numeroCompra(),
                srvValidarOFAC.validar(solicitud.numeroCompra(), solicitud.cedula()));
    }
}

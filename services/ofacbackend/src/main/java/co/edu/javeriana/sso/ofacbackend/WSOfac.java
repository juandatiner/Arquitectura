package co.edu.javeriana.sso.ofacbackend;

import co.edu.javeriana.sso.ofacbackend.modelo.RespuestaOFAC;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servicio web de la OFAC. Lo invoca IntegraOFAC (MSValidacion) por HTTP/REST. */
@RestController
@RequestMapping("/ofac")
public class WSOfac {

    private final SrvValidacionOFAC srvValidacionOFAC;

    public WSOfac(SrvValidacionOFAC srvValidacionOFAC) {
        this.srvValidacionOFAC = srvValidacionOFAC;
    }

    /** Pasos VI.18 (IntegraOFAC → WSOfac validarCedula(cedula)) y VI.19 (→ SrvValidacionOFAC). */
    @GetMapping("/{cedula}")
    public RespuestaOFAC validarCedula(@PathVariable @Pattern(regexp = "\\d{5,15}") String cedula) {
        return new RespuestaOFAC(cedula, srvValidacionOFAC.validarCedula(cedula));
    }
}

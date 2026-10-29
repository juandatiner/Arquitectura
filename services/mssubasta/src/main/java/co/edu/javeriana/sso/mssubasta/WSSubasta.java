package co.edu.javeriana.sso.mssubasta;

import co.edu.javeriana.sso.mssubasta.modelo.MotivoRechazo;
import co.edu.javeriana.sso.mssubasta.modelo.OfertaNoRegistradaException;
import co.edu.javeriana.sso.mssubasta.modelo.OfertaRegistrada;
import co.edu.javeriana.sso.mssubasta.modelo.SolicitudOferta;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Servicio web de subastas. Lo invoca SrvRuteo (APIGateway) por HTTP. */
@RestController
@RequestMapping("/subastas")
public class WSSubasta {

    private final SrvSubasta srvSubasta;

    public WSSubasta(SrvSubasta srvSubasta) {
        this.srvSubasta = srvSubasta;
    }

    /** Pasos IV.7 (SrvRuteo → WSSubasta) y IV.8 (WSSubasta → SrvSubasta). */
    @PostMapping("/ofertas")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OfertaRegistrada registrarOferta(@Valid @RequestBody SolicitudOferta solicitud) {
        return srvSubasta.registrarOferta(
                solicitud.idSubasta(), solicitud.cliente(), solicitud.valor());
    }

    /**
     * Paso IV.12b (SrvSubasta → WSSubasta): la oferta no se registró. Devuelve la excepción que el
     * servicio lanza para cortar la transacción; {@link #rechazo} la convierte en la respuesta
     * HTTP.
     */
    public OfertaNoRegistradaException ofertaNoRegistrada(MotivoRechazo motivo) {
        return ofertaNoRegistrada(
                motivo,
                "La oferta no se registró: otra oferta llegó al mismo tiempo, intente de nuevo");
    }

    public OfertaNoRegistradaException ofertaNoRegistrada(MotivoRechazo motivo, String detalle) {
        return new OfertaNoRegistradaException(motivo, detalle);
    }

    @ExceptionHandler(OfertaNoRegistradaException.class)
    ProblemDetail rechazo(OfertaNoRegistradaException e) {
        HttpStatus estado =
                e.motivo() == MotivoRechazo.CONFLICTO_CONCURRENTE
                        ? HttpStatus.CONFLICT
                        : HttpStatus.UNPROCESSABLE_CONTENT;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, e.getMessage());
        problema.setProperty("motivo", e.motivo().name());
        return problema;
    }
}

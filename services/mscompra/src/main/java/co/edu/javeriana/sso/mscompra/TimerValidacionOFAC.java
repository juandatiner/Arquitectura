package co.edu.javeriana.sso.mscompra;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Valida ante la OFAC las compras iniciadas y revisa despachos agotados (observación 12). Corre en
 * las dos réplicas.
 */
@Component
public class TimerValidacionOFAC {

    private final SrvCompraSaga srvCompraSaga;

    public TimerValidacionOFAC(SrvCompraSaga srvCompraSaga) {
        this.srvCompraSaga = srvCompraSaga;
    }

    /** Paso VI.11 (TimerValidacionOFAC → SrvCompraSaga validarComprasPendientes()). */
    @Scheduled(fixedDelayString = "${sso.compra.intervalo-validacion}")
    public void validar() {
        srvCompraSaga.validarComprasPendientes();
    }

    /** Disparo de X.9 – X.10 (despacho agotado). */
    @Scheduled(fixedDelayString = "${sso.compra.intervalo-validacion}")
    public void revisarDespachos() {
        srvCompraSaga.revisarDespachosAgotados();
    }
}

package co.edu.javeriana.sso.mssubasta;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cierra las subastas cuya fecha de cierre ya pasó. */
@Component
public class TimerCierreSubasta {

    private final SrvSubasta srvSubasta;

    public TimerCierreSubasta(SrvSubasta srvSubasta) {
        this.srvSubasta = srvSubasta;
    }

    /** Paso V.1 (TimerCierreSubasta → SrvSubasta cerrarSubastasVencidas()). */
    @Scheduled(fixedDelayString = "${sso.timers.cierre:15s}")
    public void cerrarVencidas() {
        srvSubasta.cerrarSubastasVencidas();
    }
}

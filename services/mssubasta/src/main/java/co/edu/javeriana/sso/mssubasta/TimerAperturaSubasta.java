package co.edu.javeriana.sso.mssubasta;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Abre las subastas cuando llega su fecha de inicio. */
@Component
public class TimerAperturaSubasta {

    private final SrvSubasta srvSubasta;
    private final Clock reloj;

    public TimerAperturaSubasta(SrvSubasta srvSubasta, Clock reloj) {
        this.srvSubasta = srvSubasta;
        this.reloj = reloj;
    }

    /**
     * Paso III.13 (TimerAperturaSubasta → SrvSubasta abrirSubastaProgramada(idSubasta,
     * fechaActual)).
     */
    @Scheduled(fixedDelayString = "${sso.timers.apertura:15s}")
    public void abrirProgramadas() {
        Instant fechaActual = Instant.now(reloj);
        for (String idSubasta : srvSubasta.subastasPorAbrir(fechaActual)) {
            srvSubasta.abrirSubastaProgramada(idSubasta, fechaActual);
        }
    }
}

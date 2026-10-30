package co.edu.javeriana.sso.mscompra.modelo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

/**
 * Aviso al cliente por q.notificacion (CU IX). Mismo contrato para NotificacionGanador (V.11),
 * NotificacionCancelacion (VI.23b), NotificacionDemora (VI.23c), NotificacionCompraFinalizada
 * (VIII.24) y NotificacionRevisionManual (X.10): {@code origen} lleva ese nombre.
 */
public record Notificacion(
        UUID idEvento,
        String origen,
        String numeroCompra,
        Destinatario destinatario,
        String asunto,
        String cuerpo,
        boolean sms)
        implements Evento {

    @Override
    public String cola() {
        return "q.notificacion";
    }

    @Override
    @JsonIgnore
    public String tipo() {
        return origen;
    }
}

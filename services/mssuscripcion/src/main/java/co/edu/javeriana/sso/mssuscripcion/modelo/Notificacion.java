package co.edu.javeriana.sso.mssuscripcion.modelo;

import java.util.UUID;

/**
 * III.21 / IV.19 → q.notificacion (lo consume ListenerNotificar, CU IX). Una por destinatario.
 *
 * @param origen tipo del evento que la generó (NuevaSubasta, SubastaIniciada, CambioOfertaMayor)
 * @param sms true si además del correo se envía SMS al celular (IX.6 "solo si el evento lo pide")
 */
public record Notificacion(
        UUID idEvento,
        String origen,
        String idSubasta,
        Destinatario destinatario,
        String asunto,
        String cuerpo,
        boolean sms)
        implements Evento {

    @Override
    public String cola() {
        return "q.notificacion";
    }
}

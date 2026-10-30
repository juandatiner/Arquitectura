package co.edu.javeriana.sso.msnotificacion.modelo;

import java.util.UUID;

/**
 * Parámetro {@code evento} de notificar(evento): el mensaje de q.notificacion (contrato en
 * docs/modelo/eventos.md). {@code sms} = true si el evento pide también SMS (IX.6).
 */
public record Notificacion(
        UUID idEvento,
        String origen,
        Destinatario destinatario,
        String asunto,
        String cuerpo,
        boolean sms) {}

package co.edu.javeriana.sso.mssuscripcion.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Parámetro {@code evento} de procesarEvento(evento): lo que llega por q.nuevaSubasta (III.18a),
 * q.subastaIniciada (III.18b) o q.cambioOfertaMayor (IV.16). El {@code tipo} viene en la propiedad
 * AMQP type del mensaje.
 */
public sealed interface EventoSubasta {

    UUID idEvento();

    String idSubasta();

    record NuevaSubasta(
            UUID idEvento,
            String idSubasta,
            String idBien,
            String descripcion,
            String categoria,
            BigDecimal valorMinimo,
            Instant fechaInicio,
            Instant fechaCierre,
            String publicadaPor)
            implements EventoSubasta {}

    record SubastaIniciada(
            UUID idEvento,
            String idSubasta,
            String idBien,
            String descripcion,
            BigDecimal valorMinimo,
            Instant fechaCierre)
            implements EventoSubasta {}

    record CambioOfertaMayor(
            UUID idEvento,
            String idSubasta,
            String descripcion,
            BigDecimal valor,
            Cliente cliente,
            Cliente anterior)
            implements EventoSubasta {}
}

package co.edu.javeriana.sso.mssuscripcion.modelo;

import java.time.Instant;
import java.util.UUID;

/** Suscripción registrada en DB Suscripcion. */
public record Suscripcion(
        UUID idSuscripcion,
        Cliente cliente,
        TipoSuscripcion tipo,
        String valor,
        EstadoSuscripcion estado,
        Instant creadaEn) {}

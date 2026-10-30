package co.edu.javeriana.sso.senbackend.modelo;

import java.util.UUID;

/** Fila PENDIENTE del outbox lista para publicarse. */
public record EventoPendiente(UUID idEvento, String tipo, String cola, String payload) {}

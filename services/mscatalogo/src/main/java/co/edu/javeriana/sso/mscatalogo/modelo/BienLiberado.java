package co.edu.javeriana.sso.mscatalogo.modelo;

import java.util.UUID;

/**
 * Mensaje de q.LiberarBienSubasta (X.4 → X.5b), publicado por WorkerOutboxCompra en la
 * compensación.
 */
public record BienLiberado(UUID idEvento, String numeroCompra, String idSubasta, String motivo) {}

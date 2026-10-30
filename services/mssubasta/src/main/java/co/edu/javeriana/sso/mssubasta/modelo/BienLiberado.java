package co.edu.javeriana.sso.mssubasta.modelo;

import java.util.UUID;

/** Mensaje de q.LiberarBienSubasta (X.4 → X.5a). */
public record BienLiberado(UUID idEvento, String numeroCompra, String idSubasta, String motivo) {}

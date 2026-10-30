package co.edu.javeriana.sso.mssuscripcion.modelo;

import java.util.UUID;

/** Mensaje de q.LiberarBienSubasta (X.4 → X.5c). */
public record BienLiberado(UUID idEvento, String numeroCompra, String idSubasta, String motivo) {}

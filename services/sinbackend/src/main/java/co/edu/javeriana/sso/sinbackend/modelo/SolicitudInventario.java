package co.edu.javeriana.sso.sinbackend.modelo;

import java.util.UUID;

/** Mensaje de q.inventarioSolicitud (VIII.8a → VIII.9a). */
public record SolicitudInventario(UUID idEvento, String numeroCompra, String idBien) {}

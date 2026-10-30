package co.edu.javeriana.sso.senbackend.modelo;

import java.util.UUID;

/** Mensaje de q.envioSolicitud (VIII.8b → VIII.9b). */
public record SolicitudEnvio(UUID idEvento, String numeroCompra, String idBien, String direccion) {}

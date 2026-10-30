package co.edu.javeriana.sso.msdespacho.modelo;

import java.util.UUID;

/** Mensaje de q.despachoSolicitud (VIII.2 → VIII.3), publicado por MSCompra. */
public record DespachoSolicitud(
        UUID idEvento, String numeroCompra, String idBien, String descripcion, String direccion) {}

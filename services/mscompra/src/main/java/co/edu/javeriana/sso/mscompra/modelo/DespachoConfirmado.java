package co.edu.javeriana.sso.mscompra.modelo;

import java.util.UUID;

/** Mensaje de q.despachoConfirmado (VIII.20 → VIII.21): SIN y SEN confirmaron. */
public record DespachoConfirmado(UUID idEvento, String numeroCompra) {}

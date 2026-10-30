package co.edu.javeriana.sso.msdespacho.modelo;

import java.util.UUID;

/** Lo que MSDespacho lee de InventarioConfirmado (VIII.15a) y EnvioConfirmado (VIII.15b). */
public record Confirmacion(UUID idEvento, String numeroCompra) {}

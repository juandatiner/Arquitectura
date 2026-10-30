package co.edu.javeriana.sso.sinbackend.modelo;

import java.util.UUID;

/**
 * VIII.12a / VIII.14a → q.inventarioConfirmacion (lo consume ListenerConfInventario de MSDespacho).
 */
public record InventarioConfirmado(UUID idEvento, String numeroCompra, String idBien)
        implements Evento {

    @Override
    public String cola() {
        return "q.inventarioConfirmacion";
    }
}

package co.edu.javeriana.sso.msdespacho.modelo;

import java.util.UUID;

/**
 * VIII.18 / VIII.20 → q.despachoConfirmado (lo consume ListenerConfDespacho en una réplica de
 * MSCompra).
 */
public record DespachoConfirmado(UUID idEvento, String numeroCompra) implements Evento {

    @Override
    public String cola() {
        return "q.despachoConfirmado";
    }
}

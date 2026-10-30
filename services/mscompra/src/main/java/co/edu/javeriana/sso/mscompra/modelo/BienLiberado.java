package co.edu.javeriana.sso.mscompra.modelo;

import java.util.UUID;

/** X.2 / X.4 → q.LiberarBienSubasta (fanout a MSSubasta, MSCatalogo y MSSuscripcion). */
public record BienLiberado(UUID idEvento, String numeroCompra, String idSubasta, String motivo)
        implements Evento {

    @Override
    public String cola() {
        return "q.LiberarBienSubasta";
    }
}

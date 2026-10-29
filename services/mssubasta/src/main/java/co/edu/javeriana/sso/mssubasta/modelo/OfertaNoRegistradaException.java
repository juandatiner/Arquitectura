package co.edu.javeriana.sso.mssubasta.modelo;

/** La oferta no se registró; WSSubasta la traduce a 409 (concurrencia) o 422 (regla de negocio). */
public class OfertaNoRegistradaException extends RuntimeException {

    private final MotivoRechazo motivo;

    public OfertaNoRegistradaException(MotivoRechazo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public MotivoRechazo motivo() {
        return motivo;
    }
}

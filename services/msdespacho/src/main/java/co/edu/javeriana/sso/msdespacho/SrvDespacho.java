package co.edu.javeriana.sso.msdespacho;

import co.edu.javeriana.sso.msdespacho.modelo.Despacho;
import co.edu.javeriana.sso.msdespacho.modelo.DespachoConfirmado;
import co.edu.javeriana.sso.msdespacho.modelo.SolicitudEnvio;
import co.edu.javeriana.sso.msdespacho.modelo.SolicitudInventario;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordina el despacho con los sistemas internos SIN (inventarios) y SEN (envíos). */
@Service
public class SrvDespacho {

    private final RepoDespacho repoDespacho;
    private final RepoOutboxDespacho repoOutboxDespacho;

    public SrvDespacho(RepoDespacho repoDespacho, RepoOutboxDespacho repoOutboxDespacho) {
        this.repoDespacho = repoDespacho;
        this.repoOutboxDespacho = repoOutboxDespacho;
    }

    /**
     * Pasos VIII.4 (ListenerDespachoSolicitud → SrvDespacho), VIII.5 (→ RepoDespacho
     * existePorCompra · crear(Despacho)) y VIII.6 (→ RepoOutboxDespacho
     * guardar(SolicitudInventario) · guardar(SolicitudEnvio)). Un despacho por compra.
     */
    @Transactional
    public void solicitarDespacho(String numeroCompra, String idBien, String direccion) {
        if (repoDespacho.existePorCompra(numeroCompra)) {
            return;
        }
        repoDespacho.crear(new Despacho(numeroCompra, idBien, direccion));
        repoOutboxDespacho.guardar(
                new SolicitudInventario(UUID.randomUUID(), numeroCompra, idBien));
        repoOutboxDespacho.guardar(
                new SolicitudEnvio(UUID.randomUUID(), numeroCompra, idBien, direccion));
    }

    /** Pasos VIII.16a (ListenerConfInventario → SrvDespacho), VIII.17 y VIII.18. */
    @Transactional
    public void confirmarInventario(String numeroCompra) {
        repoDespacho.marcarInventarioConfirmado(numeroCompra);
        confirmarSiAmbos(numeroCompra);
    }

    /** Pasos VIII.16b (ListenerConfEnvio → SrvDespacho), VIII.17 y VIII.18. */
    @Transactional
    public void confirmarEnvio(String numeroCompra) {
        repoDespacho.marcarEnvioConfirmado(numeroCompra);
        confirmarSiAmbos(numeroCompra);
    }

    /**
     * Paso VIII.18: guardar(DespachoConfirmado) solo si SIN y SEN ya confirmaron. El UPDATE
     * condicional sobre la fila bloqueada garantiza un único DespachoConfirmado aunque las
     * confirmaciones lleguen a la vez.
     */
    private void confirmarSiAmbos(String numeroCompra) {
        if (repoDespacho.confirmarSiAmbos(numeroCompra)) {
            repoOutboxDespacho.guardar(new DespachoConfirmado(UUID.randomUUID(), numeroCompra));
        }
    }
}

package co.edu.javeriana.sso.sinbackend;

import co.edu.javeriana.sso.sinbackend.modelo.InventarioConfirmado;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Descuenta del inventario los bienes vendidos y lo confirma. */
@Service
public class SrvInventario {

    private final RepoInventario repoInventario;
    private final RepoOutboxSIN repoOutboxSIN;

    public SrvInventario(RepoInventario repoInventario, RepoOutboxSIN repoOutboxSIN) {
        this.repoInventario = repoInventario;
        this.repoOutboxSIN = repoOutboxSIN;
    }

    /**
     * Pasos VIII.10a (ListenerInventario → SrvInventario), VIII.11a (→ RepoInventario
     * existePorCompra · registrarSalida) y VIII.12a (→ RepoOutboxSIN
     * guardar(InventarioConfirmado)). Un mensaje repetido no registra dos salidas.
     */
    @Transactional
    public void registrarSalidaInventario(String numeroCompra, String idBien) {
        if (repoInventario.existePorCompra(numeroCompra)) {
            return;
        }
        repoInventario.registrarSalida(numeroCompra, idBien);
        repoOutboxSIN.guardar(new InventarioConfirmado(UUID.randomUUID(), numeroCompra, idBien));
    }
}

package co.edu.javeriana.sso.senbackend;

import co.edu.javeriana.sso.senbackend.config.PropiedadesEnvio;
import co.edu.javeriana.sso.senbackend.modelo.EnvioConfirmado;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Programa el envío de los bienes vendidos a la dirección registrada del cliente. */
@Service
public class SrvEnvio {

    private final RepoEnvio repoEnvio;
    private final RepoOutboxSEN repoOutboxSEN;
    private final PropiedadesEnvio propiedades;
    private final Clock reloj;

    public SrvEnvio(
            RepoEnvio repoEnvio,
            RepoOutboxSEN repoOutboxSEN,
            PropiedadesEnvio propiedades,
            Clock reloj) {
        this.repoEnvio = repoEnvio;
        this.repoOutboxSEN = repoOutboxSEN;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * Pasos VIII.10b (ListenerEnvio → SrvEnvio), VIII.11b (→ RepoEnvio existePorCompra · programar)
     * y VIII.12b (→ RepoOutboxSEN guardar(EnvioConfirmado)). Un mensaje repetido no programa dos
     * envíos.
     */
    @Transactional
    public void programarEnvio(String numeroCompra, String idBien, String direccion) {
        if (repoEnvio.existePorCompra(numeroCompra)) {
            return;
        }
        LocalDate fecha = LocalDate.now(reloj).plusDays(propiedades.diasParaEnvio());
        repoEnvio.programar(numeroCompra, idBien, direccion, fecha);
        repoOutboxSEN.guardar(new EnvioConfirmado(UUID.randomUUID(), numeroCompra, fecha));
    }
}

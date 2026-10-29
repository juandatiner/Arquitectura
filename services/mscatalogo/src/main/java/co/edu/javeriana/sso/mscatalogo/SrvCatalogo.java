package co.edu.javeriana.sso.mscatalogo;

import co.edu.javeriana.sso.mscatalogo.modelo.Administrador;
import co.edu.javeriana.sso.mscatalogo.modelo.BienLiberadoConfirmado;
import co.edu.javeriana.sso.mscatalogo.modelo.EstadoSubasta;
import co.edu.javeriana.sso.mscatalogo.modelo.Filtros;
import co.edu.javeriana.sso.mscatalogo.modelo.NuevaSubasta;
import co.edu.javeriana.sso.mscatalogo.modelo.PaginaCatalogo;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaNoEncontradaException;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaNoPublicableException;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaPublicada;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lógica del catálogo. */
@Service
public class SrvCatalogo {

    private final RepoCatalogo repoCatalogo;
    private final RepoOutboxCatalogo repoOutboxCatalogo;
    private final Clock reloj;

    public SrvCatalogo(
            RepoCatalogo repoCatalogo, RepoOutboxCatalogo repoOutboxCatalogo, Clock reloj) {
        this.repoCatalogo = repoCatalogo;
        this.repoOutboxCatalogo = repoOutboxCatalogo;
        this.reloj = reloj;
    }

    /** Pasos II.8a (WSCatalogo → SrvCatalogo) y II.9a (→ RepoCatalogo). */
    @Transactional(readOnly = true)
    public PaginaCatalogo consultarCatalogo(Filtros filtros) {
        return repoCatalogo.buscarSubastasPublicadas(filtros);
    }

    /**
     * Pasos III.8, III.9 (→ RepoCatalogo publicarSiPendienteYEnFecha) y III.10 (→
     * RepoOutboxCatalogo guardar(NuevaSubasta)). El cambio de estado y el evento se guardan en la
     * misma transacción.
     */
    @Transactional
    public SubastaPublicada publicarNuevaSubasta(String idSubasta, Administrador admin) {
        Instant ahora = Instant.now(reloj);
        if (!repoCatalogo.publicarSiPendienteYEnFecha(idSubasta, admin.usuario(), ahora)) {
            SubastaPublicada actual =
                    repoCatalogo
                            .obtener(idSubasta)
                            .orElseThrow(() -> new SubastaNoEncontradaException(idSubasta));
            throw new SubastaNoPublicableException(
                    actual.estado() != EstadoSubasta.PENDIENTE
                            ? "La subasta " + idSubasta + " ya está " + actual.estado()
                            : "La subasta " + idSubasta + " ya cerró y no se puede publicar");
        }
        SubastaPublicada publicada = repoCatalogo.obtener(idSubasta).orElseThrow();
        repoOutboxCatalogo.guardar(
                new NuevaSubasta(
                        UUID.randomUUID(),
                        publicada.idSubasta(),
                        publicada.idBien(),
                        publicada.descripcion(),
                        publicada.categoria(),
                        publicada.valorMinimo(),
                        publicada.fechaInicio(),
                        publicada.fechaCierre(),
                        admin.usuario()));
        return publicada;
    }

    /**
     * Pasos X.6b (ListenerLiberarBienCat → SrvCatalogo), X.7b (→ RepoCatalogo liberarSiAdjudicada)
     * y X.8b (→ RepoOutboxCatalogo guardar(BienLiberadoConfirmado)). Idempotente: si ya estaba
     * liberada no hace nada.
     */
    @Transactional
    public void liberarBien(String idSubasta, String motivo) {
        if (repoCatalogo.liberarSiAdjudicada(idSubasta)) {
            repoOutboxCatalogo.guardar(
                    new BienLiberadoConfirmado(UUID.randomUUID(), idSubasta, motivo));
        }
    }
}

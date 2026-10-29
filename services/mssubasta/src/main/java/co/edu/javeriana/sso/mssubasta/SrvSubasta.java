package co.edu.javeriana.sso.mssubasta;

import co.edu.javeriana.sso.mssubasta.modelo.BienLiberadoConfirmado;
import co.edu.javeriana.sso.mssubasta.modelo.CambioOfertaMayor;
import co.edu.javeriana.sso.mssubasta.modelo.Cliente;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoOferta;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoSubasta;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoYVersion;
import co.edu.javeriana.sso.mssubasta.modelo.MotivoRechazo;
import co.edu.javeriana.sso.mssubasta.modelo.Oferta;
import co.edu.javeriana.sso.mssubasta.modelo.OfertaRegistrada;
import co.edu.javeriana.sso.mssubasta.modelo.SubastaCerrada;
import co.edu.javeriana.sso.mssubasta.modelo.SubastaIniciada;
import co.edu.javeriana.sso.mssubasta.modelo.SubastaNoEncontradaException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lógica de la subasta: apertura, ofertas con control de concurrencia optimista, cierre con
 * adjudicación y liberación del bien en la compensación.
 */
@Service
public class SrvSubasta {

    private static final Logger LOG = LoggerFactory.getLogger(SrvSubasta.class);

    private final RepoSubasta repoSubasta;
    private final RepoOferta repoOferta;
    private final RepoOutboxSubasta repoOutboxSubasta;

    /**
     * Flecha IV.12b (SrvSubasta → WSSubasta); diferida porque WSSubasta también depende de este
     * servicio.
     */
    private final ObjectProvider<WSSubasta> wsSubasta;

    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public SrvSubasta(
            RepoSubasta repoSubasta,
            RepoOferta repoOferta,
            RepoOutboxSubasta repoOutboxSubasta,
            ObjectProvider<WSSubasta> wsSubasta,
            TransactionTemplate transaccion,
            Clock reloj) {
        this.repoSubasta = repoSubasta;
        this.repoOferta = repoOferta;
        this.repoOutboxSubasta = repoOutboxSubasta;
        this.wsSubasta = wsSubasta;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /** Subastas PROGRAMADAS cuya hora de inicio llegó; las consulta TimerAperturaSubasta. */
    public List<String> subastasPorAbrir(Instant fechaActual) {
        return repoSubasta.buscarPorAbrir(fechaActual);
    }

    /**
     * Pasos III.13 (TimerAperturaSubasta → SrvSubasta), III.14 (→ RepoSubasta
     * abrirSiProgramadaYEnPlazo) y III.15 (→ RepoOutboxSubasta guardar(SubastaIniciada)).
     */
    public void abrirSubastaProgramada(String idSubasta, Instant fechaActual) {
        transaccion.executeWithoutResult(
                estado -> {
                    if (!repoSubasta.abrirSiProgramadaYEnPlazo(idSubasta)) {
                        return;
                    }
                    EstadoYVersion subasta =
                            repoSubasta.obtenerEstadoYVersion(idSubasta).orElseThrow();
                    repoOutboxSubasta.guardar(
                            new SubastaIniciada(
                                    UUID.randomUUID(),
                                    subasta.idSubasta(),
                                    subasta.idBien(),
                                    subasta.descripcion(),
                                    subasta.valorMinimo(),
                                    subasta.fechaCierre()));
                    LOG.info("Subasta {} abierta ({})", idSubasta, fechaActual);
                });
    }

    /**
     * Pasos IV.8 (WSSubasta → SrvSubasta), IV.9 (→ RepoSubasta obtenerEstadoYVersion), IV.10 (→
     * RepoOferta obtenerOfertaMayor), IV.11 (→ RepoSubasta actualizarVersionSiAbiertaYEnPlazo),
     * IV.12a (→ RepoOferta guardarOferta), IV.13a (→ RepoOutboxSubasta guardar(CambioOfertaMayor))
     * e IV.12b (→ WSSubasta ofertaNoRegistrada(CONFLICTO_CONCURRENTE)).
     */
    public OfertaRegistrada registrarOferta(String idSubasta, Cliente cliente, BigDecimal valor) {
        return transaccion.execute(
                estado -> {
                    EstadoYVersion subasta =
                            repoSubasta
                                    .obtenerEstadoYVersion(idSubasta)
                                    .orElseThrow(() -> new SubastaNoEncontradaException(idSubasta));
                    Optional<Oferta> mayor = repoOferta.obtenerOfertaMayor(idSubasta);
                    validarOferta(subasta, valor, mayor, Instant.now(reloj));
                    if (!repoSubasta.actualizarVersionSiAbiertaYEnPlazo(
                            idSubasta, subasta.version())) {
                        throw wsSubasta
                                .getObject()
                                .ofertaNoRegistrada(MotivoRechazo.CONFLICTO_CONCURRENTE);
                    }
                    long idOferta = repoOferta.guardarOferta(idSubasta, cliente, valor);
                    repoOutboxSubasta.guardar(
                            new CambioOfertaMayor(
                                    UUID.randomUUID(),
                                    idSubasta,
                                    subasta.descripcion(),
                                    valor,
                                    cliente,
                                    mayor.map(Oferta::cliente).orElse(null)));
                    return new OfertaRegistrada(
                            idOferta,
                            idSubasta,
                            valor,
                            EstadoOferta.REGISTRADA,
                            OfertaRegistrada.MENSAJE);
                });
    }

    /**
     * Reglas del enunciado: entre las fechas de la subasta, ≥ valor mínimo y mayor a la oferta
     * actual.
     */
    private void validarOferta(
            EstadoYVersion subasta, BigDecimal valor, Optional<Oferta> mayor, Instant ahora) {
        WSSubasta ws = wsSubasta.getObject();
        if (subasta.estado() != EstadoSubasta.ABIERTA) {
            throw ws.ofertaNoRegistrada(
                    MotivoRechazo.SUBASTA_NO_ABIERTA,
                    "La subasta " + subasta.idSubasta() + " no está abierta");
        }
        if (ahora.isBefore(subasta.fechaInicio()) || !ahora.isBefore(subasta.fechaCierre())) {
            throw ws.ofertaNoRegistrada(
                    MotivoRechazo.FUERA_DE_PLAZO,
                    "La oferta está fuera de las fechas de la subasta");
        }
        if (valor.compareTo(subasta.valorMinimo()) < 0) {
            throw ws.ofertaNoRegistrada(
                    MotivoRechazo.MENOR_AL_MINIMO,
                    "La oferta debe ser mayor o igual al valor mínimo de " + subasta.valorMinimo());
        }
        if (mayor.isPresent() && valor.compareTo(mayor.get().valor()) <= 0) {
            throw ws.ofertaNoRegistrada(
                    MotivoRechazo.NO_SUPERA_OFERTA_MAYOR,
                    "La oferta debe superar la oferta mayor actual de " + mayor.get().valor());
        }
    }

    /**
     * Pasos V.1 (TimerCierreSubasta → SrvSubasta), V.2 (→ RepoSubasta buscarVencidas ·
     * tomarParaCierre), V.3 (→ RepoOferta obtenerOfertaMayor · marcarResultado), V.4a adjudicar /
     * V.4b declararDesierta y V.5 (→ RepoOutboxSubasta guardar(SubastaCerrada)). Cada subasta se
     * cierra en su propia transacción.
     */
    public void cerrarSubastasVencidas() {
        for (String idSubasta : repoSubasta.buscarVencidas()) {
            transaccion.executeWithoutResult(estado -> cerrar(idSubasta));
        }
    }

    private void cerrar(String idSubasta) {
        if (!repoSubasta.tomarParaCierre(idSubasta)) {
            return;
        }
        EstadoYVersion subasta = repoSubasta.obtenerEstadoYVersion(idSubasta).orElseThrow();
        Optional<Oferta> mayor = repoOferta.obtenerOfertaMayor(idSubasta);
        repoOferta.marcarResultado(idSubasta, mayor.map(Oferta::idOferta).orElse(null));
        SubastaCerrada cerrada;
        if (mayor.isPresent()) {
            Oferta ganadora = mayor.get();
            repoSubasta.adjudicar(idSubasta, ganadora.cliente().cedula(), ganadora.valor());
            cerrada =
                    new SubastaCerrada(
                            UUID.randomUUID(),
                            idSubasta,
                            subasta.idBien(),
                            subasta.descripcion(),
                            EstadoSubasta.ADJUDICADA,
                            ganadora.cliente(),
                            ganadora.valor());
        } else {
            repoSubasta.declararDesierta(idSubasta);
            cerrada =
                    new SubastaCerrada(
                            UUID.randomUUID(),
                            idSubasta,
                            subasta.idBien(),
                            subasta.descripcion(),
                            EstadoSubasta.DESIERTA,
                            null,
                            null);
        }
        repoOutboxSubasta.guardar(cerrada);
        LOG.info("Subasta {} cerrada: {}", idSubasta, cerrada.resultado());
    }

    /**
     * Pasos X.6a (ListenerLiberarBienSub → SrvSubasta), X.7a (→ RepoSubasta liberarSiAdjudicada) y
     * X.8a (→ RepoOutboxSubasta guardar(BienLiberadoConfirmado)). Idempotente.
     */
    public void liberarBien(String idSubasta, String motivo) {
        transaccion.executeWithoutResult(
                estado -> {
                    if (repoSubasta.liberarSiAdjudicada(idSubasta)) {
                        repoOutboxSubasta.guardar(
                                new BienLiberadoConfirmado(UUID.randomUUID(), idSubasta, motivo));
                    }
                });
    }
}

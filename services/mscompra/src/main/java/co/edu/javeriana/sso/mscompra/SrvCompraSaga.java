package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.config.PropiedadesCompra;
import co.edu.javeriana.sso.mscompra.modelo.BienLiberado;
import co.edu.javeriana.sso.mscompra.modelo.Cliente;
import co.edu.javeriana.sso.mscompra.modelo.Compra;
import co.edu.javeriana.sso.mscompra.modelo.CompraAjenaException;
import co.edu.javeriana.sso.mscompra.modelo.CompraIniciada;
import co.edu.javeriana.sso.mscompra.modelo.CompraNoEncontradaException;
import co.edu.javeriana.sso.mscompra.modelo.CompraNoIniciableException;
import co.edu.javeriana.sso.mscompra.modelo.DespachoSolicitud;
import co.edu.javeriana.sso.mscompra.modelo.Destinatario;
import co.edu.javeriana.sso.mscompra.modelo.EstadoCompra;
import co.edu.javeriana.sso.mscompra.modelo.EstadoOFAC;
import co.edu.javeriana.sso.mscompra.modelo.Notificacion;
import co.edu.javeriana.sso.mscompra.modelo.PagoSolicitud;
import co.edu.javeriana.sso.mscompra.modelo.ReferenciaPago;
import co.edu.javeriana.sso.mscompra.modelo.ResultadoPago;
import co.edu.javeriana.sso.mscompra.modelo.SubastaCerrada;
import co.edu.javeriana.sso.mscompra.modelo.ValidacionNoDisponibleException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquestador de la saga de compra. No guarda estado en memoria: las dos réplicas comparten DB
 * COMPRA y cada transición es un UPDATE condicional, así que da igual qué réplica atienda cada
 * paso.
 */
@Service
public class SrvCompraSaga {

    private static final Logger LOG = LoggerFactory.getLogger(SrvCompraSaga.class);

    private final RepoCompra repoCompra;
    private final RepoOutboxCompra repoOutboxCompra;
    private final IntegraValidacion integraValidacion;
    private final TransactionTemplate transaccion;
    private final PropiedadesCompra propiedades;

    public SrvCompraSaga(
            RepoCompra repoCompra,
            RepoOutboxCompra repoOutboxCompra,
            IntegraValidacion integraValidacion,
            TransactionTemplate transaccion,
            PropiedadesCompra propiedades) {
        this.repoCompra = repoCompra;
        this.repoOutboxCompra = repoOutboxCompra;
        this.integraValidacion = integraValidacion;
        this.transaccion = transaccion;
        this.propiedades = propiedades;
    }

    /**
     * Pasos V.9a/b (ListenerSubastaCerrada → SrvCompraSaga), V.10a/b (→ RepoCompra existePorSubasta
     * · crear(Compra)) y V.11a/b (→ RepoOutboxCompra guardar(NotificacionGanador)). Una sola compra
     * por subasta aunque el evento llegue dos veces o a las dos réplicas.
     */
    public void iniciarSaga(SubastaCerrada evento) {
        if (!"ADJUDICADA".equals(evento.resultado()) || evento.ganador() == null) {
            return;
        }
        transaccion.executeWithoutResult(
                estado -> {
                    if (repoCompra.existePorSubasta(evento.idSubasta())) {
                        return;
                    }
                    Compra compra = repoCompra.crear(evento);
                    repoOutboxCompra.guardar(
                            aviso(
                                    "NotificacionGanador",
                                    compra,
                                    "Ganó la subasta " + compra.idSubasta(),
                                    "Su oferta por "
                                            + compra.descripcion()
                                            + " ganó la subasta. Ingrese a SSO e inicie la compra número "
                                            + compra.numeroCompra()
                                            + ".",
                                    false));
                    LOG.info(
                            "Compra {} creada para la subasta {}",
                            compra.numeroCompra(),
                            compra.idSubasta());
                });
    }

    /** Compras del cliente (VI.1, observación 6). */
    public List<Compra> consultarCompras(String cedula) {
        return repoCompra.consultarCompras(cedula);
    }

    /**
     * Pasos VI.9 (WSCompra → SrvCompraSaga) y VI.10 (→ RepoCompra obtener ·
     * cambiarEstado(EN_VALIDACION)). La validación OFAC la hace después TimerValidacionOFAC: el
     * cliente no espera.
     */
    public CompraIniciada iniciarCompra(String numeroCompra, Cliente cliente) {
        return transaccion.execute(
                estado -> {
                    Compra compra =
                            repoCompra
                                    .obtener(numeroCompra)
                                    .orElseThrow(
                                            () -> new CompraNoEncontradaException(numeroCompra));
                    if (!compra.cliente().cedula().equals(cliente.cedula())) {
                        throw new CompraAjenaException();
                    }
                    if (compra.estado() == EstadoCompra.EN_VALIDACION) {
                        return new CompraIniciada(
                                numeroCompra, compra.estado(), CompraIniciada.MENSAJE);
                    }
                    if (compra.estado() != EstadoCompra.CREADA) {
                        throw new CompraNoIniciableException(compra.estado());
                    }
                    repoCompra.registrarDireccion(numeroCompra, cliente.direccion());
                    repoCompra.cambiarEstado(numeroCompra, EstadoCompra.EN_VALIDACION, null);
                    return new CompraIniciada(
                            numeroCompra, EstadoCompra.EN_VALIDACION, CompraIniciada.MENSAJE);
                });
    }

    /**
     * Pasos VI.11 (TimerValidacionOFAC → SrvCompraSaga), VI.12 (→ RepoCompra
     * tomarPendientesValidacion), VI.13 (→ IntegraValidacion validar) y VI.22a–VI.23c según la
     * respuesta.
     */
    public void validarComprasPendientes() {
        List<Compra> pendientes =
                repoCompra.tomarPendientesValidacion(
                        propiedades.tamanoLote(), propiedades.reservaValidacion());
        for (Compra compra : pendientes) {
            EstadoOFAC resultado;
            try {
                resultado =
                        integraValidacion.validar(compra.numeroCompra(), compra.cliente().cedula());
            } catch (ValidacionNoDisponibleException e) {
                LOG.warn(
                        "Validación de {} sin respuesta: {}",
                        compra.numeroCompra(),
                        e.getMessage());
                resultado = null;
            }
            EstadoOFAC respuesta = resultado;
            transaccion.executeWithoutResult(estado -> procesarValidacion(compra, respuesta));
        }
    }

    /**
     * a: APROBADO (VI.22a, VI.23a) · b: RECHAZADO (VI.22b, VI.23b) · c: ENPROCESO o sin respuesta
     * (VI.22c, VI.23c).
     */
    private void procesarValidacion(Compra compra, EstadoOFAC resultado) {
        String numero = compra.numeroCompra();
        if (resultado == EstadoOFAC.APROBADO) {
            repoCompra.cambiarEstado(numero, EstadoCompra.ESPERANDO_PAGO, null);
            repoOutboxCompra.guardar(
                    new PagoSolicitud(
                            UUID.randomUUID(),
                            numero,
                            compra.cliente().cedula(),
                            compra.valorOfertado(),
                            compra.descripcion(),
                            destinatario(compra)));
        } else if (resultado == EstadoOFAC.RECHAZADO) {
            repoCompra.cambiarEstado(numero, EstadoCompra.CANCELADA, "RECHAZADO OFAC");
            repoOutboxCompra.guardar(
                    aviso(
                            "NotificacionCancelacion",
                            compra,
                            "Compra " + numero + " cancelada",
                            "No es posible continuar la compra "
                                    + numero
                                    + " de "
                                    + compra.descripcion()
                                    + ": la validación ante la OFAC fue rechazada.",
                            true));
        } else {
            int intento = compra.intentosOfac() + 1;
            if (intento < propiedades.maximoIntentosOfac()) {
                repoCompra.registrarIntento(numero, intento, espera(intento));
                return;
            }
            repoCompra.registrarIntento(numero, intento, null);
            repoCompra.cambiarEstado(
                    numero,
                    EstadoCompra.EN_REVISION,
                    "OFAC sin respuesta tras " + intento + " intentos");
            repoOutboxCompra.guardar(
                    aviso(
                            "NotificacionDemora",
                            compra,
                            "Compra " + numero + " en revisión",
                            "La validación de su compra "
                                    + numero
                                    + " está tardando más de lo normal. Un asesor la revisará y le avisaremos.",
                            true));
        }
    }

    /** Espera exponencial entre intentos de validación, con tope. */
    private Duration espera(int intento) {
        Duration espera =
                propiedades.esperaInicialOfac().multipliedBy(1L << Math.min(intento - 1, 10));
        return espera.compareTo(propiedades.esperaMaximaOfac()) > 0
                ? propiedades.esperaMaximaOfac()
                : espera;
    }

    /**
     * Pasos VII.31 (ListenerPago → SrvCompraSaga), VII.32a (→ RepoCompra
     * cambiarEstadoSiActual(ESPERANDO_PAGO → PAGO_CONFIRMADO)), VII.33a (→ RepoOutboxCompra
     * guardar(DespachoSolicitud)) y VII.32b (→ compensar).
     */
    public void procesarPago(String numeroCompra, ReferenciaPago referenciaPago) {
        if (referenciaPago.resultado() == ResultadoPago.RECHAZADO) {
            compensar(numeroCompra, "pago rechazado");
            return;
        }
        transaccion.executeWithoutResult(
                estado -> {
                    if (!repoCompra.cambiarEstadoSiActual(
                            numeroCompra,
                            EstadoCompra.ESPERANDO_PAGO,
                            EstadoCompra.PAGO_CONFIRMADO)) {
                        return;
                    }
                    repoCompra.registrarPago(numeroCompra, referenciaPago.referencia());
                    Compra compra = repoCompra.obtener(numeroCompra).orElseThrow();
                    repoOutboxCompra.guardar(
                            new DespachoSolicitud(
                                    UUID.randomUUID(),
                                    numeroCompra,
                                    compra.idBien(),
                                    compra.descripcion(),
                                    compra.cliente().direccion(),
                                    destinatario(compra)));
                });
    }

    /**
     * Pasos VIII.22 (ListenerConfDespacho → SrvCompraSaga), VIII.23 (→ RepoCompra
     * cambiarEstadoSiActual(PAGO_CONFIRMADO → TERMINADA)) y VIII.24 (→ RepoOutboxCompra
     * guardar(NotificacionCompraFinalizada)).
     */
    public void confirmarDespacho(String numeroCompra) {
        transaccion.executeWithoutResult(
                estado -> {
                    if (!repoCompra.cambiarEstadoSiActual(
                            numeroCompra, EstadoCompra.PAGO_CONFIRMADO, EstadoCompra.TERMINADA)) {
                        return;
                    }
                    Compra compra = repoCompra.obtener(numeroCompra).orElseThrow();
                    repoOutboxCompra.guardar(
                            aviso(
                                    "NotificacionCompraFinalizada",
                                    compra,
                                    "Compra " + numeroCompra + " realizada",
                                    "Su compra "
                                            + numeroCompra
                                            + " de "
                                            + compra.descripcion()
                                            + " fue realizada. El envío está programado a "
                                            + compra.cliente().direccion()
                                            + ".",
                                    true));
                });
    }

    /**
     * Paso VII.32b (SrvCompraSaga → SrvCompraSaga compensar) y CU X: X.1 (→ RepoCompra
     * cambiarEstado(CANCELADA, motivo)) y X.2 (→ RepoOutboxCompra guardar(BienLiberado)).
     * Idempotente.
     */
    public void compensar(String numeroCompra, String motivo) {
        transaccion.executeWithoutResult(
                estado -> {
                    if (!repoCompra.cambiarEstadoSiActual(
                            numeroCompra, EstadoCompra.ESPERANDO_PAGO, EstadoCompra.CANCELADA)) {
                        return;
                    }
                    repoCompra.cambiarEstado(numeroCompra, EstadoCompra.CANCELADA, motivo);
                    Compra compra = repoCompra.obtener(numeroCompra).orElseThrow();
                    repoOutboxCompra.guardar(
                            new BienLiberado(
                                    UUID.randomUUID(), numeroCompra, compra.idSubasta(), motivo));
                    LOG.info("Compra {} compensada: {}", numeroCompra, motivo);
                });
    }

    /**
     * Pasos X.9 (→ RepoCompra cambiarEstadoSiActual(PAGO_CONFIRMADO → EN_REVISION)) y X.10 (→
     * RepoOutboxCompra guardar(NotificacionRevisionManual)) para los despachos sin confirmar dentro
     * del plazo (despacho agotado, observación 12). No libera el bien.
     */
    public void revisarDespachosAgotados() {
        for (String numeroCompra :
                repoCompra.buscarDespachosAgotados(propiedades.plazoDespacho())) {
            transaccion.executeWithoutResult(
                    estado -> {
                        if (!repoCompra.cambiarEstadoSiActual(
                                numeroCompra,
                                EstadoCompra.PAGO_CONFIRMADO,
                                EstadoCompra.EN_REVISION)) {
                            return;
                        }
                        Compra compra = repoCompra.obtener(numeroCompra).orElseThrow();
                        repoOutboxCompra.guardar(
                                aviso(
                                        "NotificacionRevisionManual",
                                        compra,
                                        "Compra " + numeroCompra + " en revisión",
                                        "El despacho de su compra "
                                                + numeroCompra
                                                + " está tardando. Un asesor la revisará y le avisaremos.",
                                        true));
                    });
        }
    }

    private static Destinatario destinatario(Compra compra) {
        return new Destinatario(
                compra.cliente().nombre(), compra.cliente().correo(), compra.cliente().celular());
    }

    private static Notificacion aviso(
            String origen, Compra compra, String asunto, String cuerpo, boolean sms) {
        return new Notificacion(
                UUID.randomUUID(),
                origen,
                compra.numeroCompra(),
                destinatario(compra),
                asunto,
                cuerpo,
                sms);
    }
}

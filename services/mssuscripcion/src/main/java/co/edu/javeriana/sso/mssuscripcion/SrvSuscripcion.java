package co.edu.javeriana.sso.mssuscripcion;

import co.edu.javeriana.sso.mssuscripcion.config.PropiedadesSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.BienLiberadoConfirmado;
import co.edu.javeriana.sso.mssuscripcion.modelo.Cliente;
import co.edu.javeriana.sso.mssuscripcion.modelo.CriterioSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.Destinatario;
import co.edu.javeriana.sso.mssuscripcion.modelo.EventoSubasta;
import co.edu.javeriana.sso.mssuscripcion.modelo.Notificacion;
import co.edu.javeriana.sso.mssuscripcion.modelo.Suscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.SuscripcionNoEncontradaException;
import co.edu.javeriana.sso.mssuscripcion.modelo.TipoSuscripcion;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lógica de suscripciones y de los avisos a suscriptores. */
@Service
public class SrvSuscripcion {

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final RepoSuscripcion repoSuscripcion;
    private final RepoOutboxSuscripcion repoOutboxSuscripcion;
    private final DateTimeFormatter formatoFecha;

    public SrvSuscripcion(
            RepoSuscripcion repoSuscripcion,
            RepoOutboxSuscripcion repoOutboxSuscripcion,
            PropiedadesSuscripcion propiedades) {
        this.repoSuscripcion = repoSuscripcion;
        this.repoOutboxSuscripcion = repoOutboxSuscripcion;
        this.formatoFecha =
                DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' h:mm a", COLOMBIA)
                        .withZone(propiedades.zonaHoraria());
    }

    /**
     * Pasos II.8b (WSSuscripcion → SrvSuscripcion suscribirse) e II.9b (→ RepoSuscripcion
     * activarSuscripcion).
     */
    @Transactional
    public Suscripcion suscribirse(CriterioSuscripcion criterio, Cliente cliente) {
        return repoSuscripcion.activarSuscripcion(criterio, cliente);
    }

    /**
     * Pasos II.8b (cancelarSuscripcion) e II.9b (→ RepoSuscripcion cancelarSuscripcion). Solo el
     * dueño.
     */
    @Transactional
    public Suscripcion cancelarSuscripcion(UUID idSuscripcion, Cliente cliente) {
        return repoSuscripcion
                .cancelarSuscripcion(idSuscripcion, cliente.cedula())
                .orElseThrow(() -> new SuscripcionNoEncontradaException(idSuscripcion));
    }

    /**
     * Pasos III.19 / IV.17 (ListenerEventoSubasta → SrvSuscripcion), III.20 / IV.18 (→
     * RepoSuscripcion yaProcesado · buscarSuscriptores) y III.21 / IV.19 (→ RepoOutboxSuscripcion
     * guardar(Notificacion)). Evento duplicado → no hace nada.
     */
    @Transactional
    public void procesarEvento(EventoSubasta evento) {
        if (repoSuscripcion.yaProcesado(evento.idEvento())) {
            return;
        }
        switch (evento) {
            case EventoSubasta.NuevaSubasta e -> {
                repoSuscripcion.registrarSubasta(
                        e.idSubasta(), e.descripcion(), e.categoria(), "PUBLICADA");
                for (Suscripcion s : suscriptores(e.categoria(), null)) {
                    avisar(
                            e,
                            s,
                            "Nueva subasta: " + e.descripcion(),
                            "Se publicó la subasta "
                                    + e.idSubasta()
                                    + " de "
                                    + e.descripcion()
                                    + ". Valor mínimo de oferta: "
                                    + pesos(e.valorMinimo())
                                    + ". Recibe ofertas desde el "
                                    + fecha(e.fechaInicio())
                                    + " hasta el "
                                    + fecha(e.fechaCierre())
                                    + " (hora de Colombia).",
                            false);
                }
            }
            case EventoSubasta.SubastaIniciada e -> {
                String categoria = repoSuscripcion.categoriaDe(e.idSubasta()).orElse(null);
                repoSuscripcion.registrarSubasta(
                        e.idSubasta(), e.descripcion(), categoria, "INICIADA");
                for (Suscripcion s : suscriptores(categoria, e.idSubasta())) {
                    avisar(
                            e,
                            s,
                            "Subasta abierta: " + e.descripcion(),
                            "La subasta "
                                    + e.idSubasta()
                                    + " de "
                                    + e.descripcion()
                                    + " ya recibe ofertas desde "
                                    + pesos(e.valorMinimo())
                                    + " hasta el "
                                    + fecha(e.fechaCierre())
                                    + " (hora de Colombia).",
                            false);
                }
            }
            case EventoSubasta.CambioOfertaMayor e -> {
                for (Suscripcion s :
                        repoSuscripcion.buscarSuscriptores(
                                TipoSuscripcion.CAMBIOS_OFERTA, List.of(e.idSubasta()))) {
                    if (s.cliente().cedula().equals(e.cliente().cedula())) {
                        continue;
                    }
                    avisar(
                            e,
                            s,
                            "Nueva oferta mayor: " + e.descripcion(),
                            "La subasta "
                                    + e.idSubasta()
                                    + " de "
                                    + e.descripcion()
                                    + " tiene una nueva oferta mayor de "
                                    + pesos(e.valor())
                                    + ".",
                            true);
                }
            }
        }
    }

    /**
     * Pasos X.6c (ListenerLiberarBienSus → SrvSuscripcion), X.7c (→ RepoSuscripcion
     * liberarSiAdjudicada) y X.8c (→ RepoOutboxSuscripcion guardar(BienLiberadoConfirmado)).
     */
    @Transactional
    public void liberarBien(String idSubasta, String motivo) {
        if (repoSuscripcion.liberarSiAdjudicada(idSubasta)) {
            repoOutboxSuscripcion.guardar(
                    new BienLiberadoConfirmado(UUID.randomUUID(), idSubasta, motivo));
        }
    }

    /**
     * Suscriptores a nuevas subastas de la categoría (o TODAS) y, si aplica, a cambios de esa
     * subasta. Uno por cédula.
     */
    private List<Suscripcion> suscriptores(String categoria, String idSubasta) {
        Map<String, Suscripcion> porCedula = new LinkedHashMap<>();
        List<String> valores = new ArrayList<>(List.of(CriterioSuscripcion.TODAS));
        if (categoria != null) {
            valores.add(categoria);
        }
        repoSuscripcion
                .buscarSuscriptores(TipoSuscripcion.NUEVAS_SUBASTAS, valores)
                .forEach(s -> porCedula.putIfAbsent(s.cliente().cedula(), s));
        if (idSubasta != null) {
            repoSuscripcion
                    .buscarSuscriptores(TipoSuscripcion.CAMBIOS_OFERTA, List.of(idSubasta))
                    .forEach(s -> porCedula.putIfAbsent(s.cliente().cedula(), s));
        }
        return List.copyOf(porCedula.values());
    }

    private void avisar(
            EventoSubasta evento, Suscripcion s, String asunto, String cuerpo, boolean sms) {
        repoOutboxSuscripcion.guardar(
                new Notificacion(
                        UUID.randomUUID(),
                        evento.getClass().getSimpleName(),
                        evento.idSubasta(),
                        new Destinatario(
                                s.cliente().nombre(), s.cliente().correo(), s.cliente().celular()),
                        asunto,
                        cuerpo,
                        sms));
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(COLOMBIA);
        formato.setMaximumFractionDigits(0);
        return formato.format(valor);
    }

    private String fecha(Instant instante) {
        return formatoFecha.format(instante);
    }
}

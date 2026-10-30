package co.edu.javeriana.sso.mssubasta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.mssubasta.modelo.CambioOfertaMayor;
import co.edu.javeriana.sso.mssubasta.modelo.Cliente;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoOferta;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoSubasta;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoYVersion;
import co.edu.javeriana.sso.mssubasta.modelo.MotivoRechazo;
import co.edu.javeriana.sso.mssubasta.modelo.Oferta;
import co.edu.javeriana.sso.mssubasta.modelo.OfertaNoRegistradaException;
import co.edu.javeriana.sso.mssubasta.modelo.SubastaCerrada;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SrvSubastaTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T12:00:00Z");
    private static final Cliente ANA =
            new Cliente("cliente1", "1010101010", "Ana", "300", "a@x", "CLIENTE");
    private static final Cliente LUIS =
            new Cliente("cliente2", "1020202020", "Luis", "301", "l@x", "CLIENTE");

    private final RepoSubasta repoSubasta = mock(RepoSubasta.class);
    private final RepoOferta repoOferta = mock(RepoOferta.class);
    private final RepoOutboxSubasta repoOutbox = mock(RepoOutboxSubasta.class);
    private final SrvSubasta srvSubasta;

    @SuppressWarnings("unchecked")
    SrvSubastaTest() {
        ObjectProvider<WSSubasta> ws = mock(ObjectProvider.class);
        when(ws.getObject()).thenReturn(new WSSubasta(null));
        srvSubasta =
                new SrvSubasta(
                        repoSubasta,
                        repoOferta,
                        repoOutbox,
                        ws,
                        new TransactionTemplate(mock(PlatformTransactionManager.class)),
                        Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void subasta(EstadoSubasta estado) {
        when(repoSubasta.obtenerEstadoYVersion("SUB-1"))
                .thenReturn(
                        Optional.of(
                                new EstadoYVersion(
                                        "SUB-1",
                                        "BIEN-1",
                                        "Camioneta",
                                        new BigDecimal("1000"),
                                        AHORA.minusSeconds(60),
                                        AHORA.plusSeconds(3600),
                                        estado,
                                        7)));
    }

    private static Oferta oferta(Cliente cliente, String valor) {
        return new Oferta(1, "SUB-1", cliente, new BigDecimal(valor), EstadoOferta.REGISTRADA);
    }

    @Test
    void ofertaValidaUsaLaVersionLeidaYAvisaAlAnterior() {
        subasta(EstadoSubasta.ABIERTA);
        when(repoOferta.obtenerOfertaMayor("SUB-1")).thenReturn(Optional.of(oferta(LUIS, "1500")));
        when(repoSubasta.actualizarVersionSiAbiertaYEnPlazo("SUB-1", 7)).thenReturn(true);
        when(repoOferta.guardarOferta("SUB-1", ANA, new BigDecimal("2000"))).thenReturn(42L);

        var registrada = srvSubasta.registrarOferta("SUB-1", ANA, new BigDecimal("2000"));

        assertThat(registrada.idOferta()).isEqualTo(42);
        ArgumentCaptor<CambioOfertaMayor> evento = ArgumentCaptor.forClass(CambioOfertaMayor.class);
        verify(repoOutbox).guardar(evento.capture());
        assertThat(evento.getValue().cliente()).isEqualTo(ANA);
        assertThat(evento.getValue().anterior()).isEqualTo(LUIS);
    }

    @Test
    void siOtraOfertaCambioLaVersionEsConflictoConcurrente() {
        subasta(EstadoSubasta.ABIERTA);
        when(repoOferta.obtenerOfertaMayor("SUB-1")).thenReturn(Optional.empty());
        when(repoSubasta.actualizarVersionSiAbiertaYEnPlazo("SUB-1", 7)).thenReturn(false);

        assertThatThrownBy(() -> srvSubasta.registrarOferta("SUB-1", ANA, new BigDecimal("2000")))
                .isInstanceOf(OfertaNoRegistradaException.class)
                .extracting(e -> ((OfertaNoRegistradaException) e).motivo())
                .isEqualTo(MotivoRechazo.CONFLICTO_CONCURRENTE);
        verify(repoOferta, never()).guardarOferta(anyString(), any(), any());
        verify(repoOutbox, never()).guardar(any());
    }

    @Test
    void ofertaMenorAlMinimoOQueNoSuperaLaMayorSeRechaza() {
        subasta(EstadoSubasta.ABIERTA);
        when(repoOferta.obtenerOfertaMayor("SUB-1")).thenReturn(Optional.of(oferta(LUIS, "1500")));

        assertThatThrownBy(() -> srvSubasta.registrarOferta("SUB-1", ANA, new BigDecimal("999")))
                .extracting(e -> ((OfertaNoRegistradaException) e).motivo())
                .isEqualTo(MotivoRechazo.MENOR_AL_MINIMO);
        assertThatThrownBy(() -> srvSubasta.registrarOferta("SUB-1", ANA, new BigDecimal("1500")))
                .extracting(e -> ((OfertaNoRegistradaException) e).motivo())
                .isEqualTo(MotivoRechazo.NO_SUPERA_OFERTA_MAYOR);
        verify(repoSubasta, never()).actualizarVersionSiAbiertaYEnPlazo(anyString(), anyLong());
    }

    @Test
    void subastaCerradaNoAceptaOfertas() {
        subasta(EstadoSubasta.ADJUDICADA);
        when(repoOferta.obtenerOfertaMayor("SUB-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> srvSubasta.registrarOferta("SUB-1", ANA, new BigDecimal("5000")))
                .extracting(e -> ((OfertaNoRegistradaException) e).motivo())
                .isEqualTo(MotivoRechazo.SUBASTA_NO_ABIERTA);
    }

    @Test
    void alCerrarConOfertasAdjudicaAlMayor() {
        subasta(EstadoSubasta.EN_CIERRE);
        when(repoSubasta.buscarVencidas()).thenReturn(List.of("SUB-1"));
        when(repoSubasta.tomarParaCierre("SUB-1")).thenReturn(true);
        when(repoOferta.obtenerOfertaMayor("SUB-1")).thenReturn(Optional.of(oferta(ANA, "3000")));

        srvSubasta.cerrarSubastasVencidas();

        verify(repoOferta).marcarResultado("SUB-1", 1L);
        verify(repoSubasta).adjudicar("SUB-1", "1010101010", new BigDecimal("3000"));
        ArgumentCaptor<SubastaCerrada> evento = ArgumentCaptor.forClass(SubastaCerrada.class);
        verify(repoOutbox).guardar(evento.capture());
        assertThat(evento.getValue().resultado()).isEqualTo(EstadoSubasta.ADJUDICADA);
        assertThat(evento.getValue().ganador()).isEqualTo(ANA);
    }

    @Test
    void alCerrarSinOfertasLaDeclaraDesierta() {
        subasta(EstadoSubasta.EN_CIERRE);
        when(repoSubasta.buscarVencidas()).thenReturn(List.of("SUB-1"));
        when(repoSubasta.tomarParaCierre("SUB-1")).thenReturn(true);
        when(repoOferta.obtenerOfertaMayor("SUB-1")).thenReturn(Optional.empty());

        srvSubasta.cerrarSubastasVencidas();

        verify(repoSubasta).declararDesierta("SUB-1");
        verify(repoSubasta, never()).adjudicar(anyString(), anyString(), any());
    }
}

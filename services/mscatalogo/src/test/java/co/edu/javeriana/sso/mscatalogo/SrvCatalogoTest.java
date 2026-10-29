package co.edu.javeriana.sso.mscatalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.mscatalogo.modelo.Administrador;
import co.edu.javeriana.sso.mscatalogo.modelo.BienLiberadoConfirmado;
import co.edu.javeriana.sso.mscatalogo.modelo.EstadoSubasta;
import co.edu.javeriana.sso.mscatalogo.modelo.NuevaSubasta;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaNoPublicableException;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaPublicada;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SrvCatalogoTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T12:00:00Z");
    private static final Administrador ADMIN =
            new Administrador("admin1", "1090909090", "Administrador", null, null, "ADMIN");

    private final RepoCatalogo repoCatalogo = mock(RepoCatalogo.class);
    private final RepoOutboxCatalogo repoOutbox = mock(RepoOutboxCatalogo.class);
    private final SrvCatalogo srvCatalogo =
            new SrvCatalogo(repoCatalogo, repoOutbox, Clock.fixed(AHORA, ZoneOffset.UTC));

    private static SubastaPublicada subasta(EstadoSubasta estado) {
        return new SubastaPublicada(
                "SUB-0001",
                "BIEN-0001",
                "Camioneta",
                "VEHICULOS",
                new BigDecimal("95000000"),
                AHORA.plusSeconds(3600),
                AHORA.plusSeconds(86400),
                estado);
    }

    @Test
    void publicarGuardaNuevaSubastaEnElOutbox() {
        when(repoCatalogo.publicarSiPendienteYEnFecha("SUB-0001", "admin1", AHORA))
                .thenReturn(true);
        when(repoCatalogo.obtener("SUB-0001"))
                .thenReturn(Optional.of(subasta(EstadoSubasta.PUBLICADA)));

        srvCatalogo.publicarNuevaSubasta("SUB-0001", ADMIN);

        ArgumentCaptor<NuevaSubasta> evento = ArgumentCaptor.forClass(NuevaSubasta.class);
        verify(repoOutbox).guardar(evento.capture());
        assertThat(evento.getValue().idSubasta()).isEqualTo("SUB-0001");
        assertThat(evento.getValue().publicadaPor()).isEqualTo("admin1");
        assertThat(evento.getValue().cola()).isEqualTo("q.nuevaSubasta");
    }

    @Test
    void publicarUnaYaPublicadaNoGeneraEvento() {
        when(repoCatalogo.publicarSiPendienteYEnFecha("SUB-0001", "admin1", AHORA))
                .thenReturn(false);
        when(repoCatalogo.obtener("SUB-0001"))
                .thenReturn(Optional.of(subasta(EstadoSubasta.PUBLICADA)));

        assertThatThrownBy(() -> srvCatalogo.publicarNuevaSubasta("SUB-0001", ADMIN))
                .isInstanceOf(SubastaNoPublicableException.class)
                .hasMessageContaining("PUBLICADA");
        verify(repoOutbox, never()).guardar(any());
    }

    @Test
    void liberarBienSoloRegistraConfirmacionSiCambioElEstado() {
        when(repoCatalogo.liberarSiAdjudicada("SUB-0001")).thenReturn(true, false);

        srvCatalogo.liberarBien("SUB-0001", "pago rechazado");
        srvCatalogo.liberarBien("SUB-0001", "pago rechazado");

        verify(repoOutbox).guardar(any(BienLiberadoConfirmado.class));
    }
}

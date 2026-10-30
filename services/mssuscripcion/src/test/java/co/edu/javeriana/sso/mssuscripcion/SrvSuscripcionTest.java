package co.edu.javeriana.sso.mssuscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.mssuscripcion.config.PropiedadesSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.Cliente;
import co.edu.javeriana.sso.mssuscripcion.modelo.EstadoSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.EventoSubasta;
import co.edu.javeriana.sso.mssuscripcion.modelo.Notificacion;
import co.edu.javeriana.sso.mssuscripcion.modelo.Suscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.TipoSuscripcion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SrvSuscripcionTest {

    private final RepoSuscripcion repo = mock(RepoSuscripcion.class);
    private final RepoOutboxSuscripcion outbox = mock(RepoOutboxSuscripcion.class);
    private final SrvSuscripcion srv =
            new SrvSuscripcion(
                    repo, outbox, new PropiedadesSuscripcion(ZoneId.of("America/Bogota")));

    private static Suscripcion suscripcion(String cedula, TipoSuscripcion tipo, String valor) {
        return new Suscripcion(
                UUID.randomUUID(),
                new Cliente(
                        "u" + cedula, cedula, "N" + cedula, "300" + cedula, cedula + "@x", null),
                tipo,
                valor,
                EstadoSuscripcion.ACTIVA,
                Instant.now());
    }

    @Test
    void nuevaSubastaAvisaUnaVezACadaSuscriptorDeLaCategoriaODeTodas() {
        when(repo.buscarSuscriptores(eq(TipoSuscripcion.NUEVAS_SUBASTAS), any()))
                .thenReturn(
                        List.of(
                                suscripcion("1", TipoSuscripcion.NUEVAS_SUBASTAS, "TODAS"),
                                suscripcion("2", TipoSuscripcion.NUEVAS_SUBASTAS, "VEHICULOS"),
                                suscripcion("1", TipoSuscripcion.NUEVAS_SUBASTAS, "VEHICULOS")));

        srv.procesarEvento(
                new EventoSubasta.NuevaSubasta(
                        UUID.randomUUID(),
                        "SUB-1",
                        "BIEN-1",
                        "Camioneta",
                        "VEHICULOS",
                        new BigDecimal("95000000"),
                        Instant.parse("2026-11-01T13:00:00Z"),
                        Instant.parse("2026-11-04T23:00:00Z"),
                        "admin1"));

        ArgumentCaptor<Notificacion> avisos = ArgumentCaptor.forClass(Notificacion.class);
        verify(outbox, times(2)).guardar(avisos.capture());
        assertThat(avisos.getAllValues())
                .extracting(n -> n.destinatario().correo())
                .containsExactly("1@x", "2@x");
        assertThat(avisos.getValue().cuerpo()).contains("$").contains("95.000.000");
        assertThat(avisos.getValue().sms()).isFalse();
    }

    @Test
    void cambioDeOfertaMayorAvisaPorSmsSinAvisarAlQueOferto() {
        when(repo.buscarSuscriptores(TipoSuscripcion.CAMBIOS_OFERTA, List.of("SUB-1")))
                .thenReturn(
                        List.of(
                                suscripcion("1", TipoSuscripcion.CAMBIOS_OFERTA, "SUB-1"),
                                suscripcion("2", TipoSuscripcion.CAMBIOS_OFERTA, "SUB-1")));

        srv.procesarEvento(
                new EventoSubasta.CambioOfertaMayor(
                        UUID.randomUUID(),
                        "SUB-1",
                        "Camioneta",
                        new BigDecimal("99000000"),
                        new Cliente("u2", "2", null, null, null, null),
                        null));

        ArgumentCaptor<Notificacion> avisos = ArgumentCaptor.forClass(Notificacion.class);
        verify(outbox).guardar(avisos.capture());
        assertThat(avisos.getValue().destinatario().celular()).isEqualTo("3001");
        assertThat(avisos.getValue().sms()).isTrue();
    }

    @Test
    void eventoDuplicadoNoGeneraAvisos() {
        when(repo.yaProcesado(any())).thenReturn(true);

        srv.procesarEvento(
                new EventoSubasta.SubastaIniciada(
                        UUID.randomUUID(),
                        "SUB-1",
                        "BIEN-1",
                        "Camioneta",
                        BigDecimal.TEN,
                        Instant.now()));

        verify(outbox, never()).guardar(any());
    }
}

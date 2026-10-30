package co.edu.javeriana.sso.mscompra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.mscompra.config.PropiedadesCompra;
import co.edu.javeriana.sso.mscompra.modelo.BienLiberado;
import co.edu.javeriana.sso.mscompra.modelo.Cliente;
import co.edu.javeriana.sso.mscompra.modelo.Compra;
import co.edu.javeriana.sso.mscompra.modelo.CompraAjenaException;
import co.edu.javeriana.sso.mscompra.modelo.EstadoCompra;
import co.edu.javeriana.sso.mscompra.modelo.EstadoOFAC;
import co.edu.javeriana.sso.mscompra.modelo.Notificacion;
import co.edu.javeriana.sso.mscompra.modelo.PagoSolicitud;
import co.edu.javeriana.sso.mscompra.modelo.ReferenciaPago;
import co.edu.javeriana.sso.mscompra.modelo.ResultadoPago;
import co.edu.javeriana.sso.mscompra.modelo.ValidacionNoDisponibleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SrvCompraSagaTest {

    private static final Cliente ANA =
            new Cliente("ana", "1010101010", "Ana", "3001110001", "ana@x.co", "Cra 7", null);

    private final RepoCompra repo = mock(RepoCompra.class);
    private final RepoOutboxCompra outbox = mock(RepoOutboxCompra.class);
    private final IntegraValidacion integra = mock(IntegraValidacion.class);
    private final SrvCompraSaga srv =
            new SrvCompraSaga(
                    repo,
                    outbox,
                    integra,
                    new TransactionTemplate(mock(PlatformTransactionManager.class)),
                    new PropiedadesCompra(
                            "http://x",
                            20,
                            Duration.ofSeconds(60),
                            3,
                            Duration.ofSeconds(10),
                            Duration.ofMinutes(5),
                            Duration.ofMinutes(30),
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(1)));

    private static Compra compra(EstadoCompra estado, int intentos) {
        return new Compra(
                "C-2026-000001",
                "SUB-1",
                "BIEN-1",
                "Camioneta",
                ANA,
                new BigDecimal("1000"),
                estado,
                null,
                intentos,
                Instant.now());
    }

    private void pendiente(int intentos) {
        when(repo.tomarPendientesValidacion(anyInt(), any()))
                .thenReturn(List.of(compra(EstadoCompra.EN_VALIDACION, intentos)));
    }

    @Test
    void ofacAprobadoPasaAEsperandoPagoYSolicitaElPago() {
        pendiente(0);
        when(integra.validar("C-2026-000001", "1010101010")).thenReturn(EstadoOFAC.APROBADO);

        srv.validarComprasPendientes();

        verify(repo).cambiarEstado("C-2026-000001", EstadoCompra.ESPERANDO_PAGO, null);
        verify(outbox).guardar(any(PagoSolicitud.class));
    }

    @Test
    void ofacRechazadoCancelaYAvisa() {
        pendiente(0);
        when(integra.validar(any(), any())).thenReturn(EstadoOFAC.RECHAZADO);

        srv.validarComprasPendientes();

        verify(repo).cambiarEstado("C-2026-000001", EstadoCompra.CANCELADA, "RECHAZADO OFAC");
        verify(outbox).guardar(any(Notificacion.class));
    }

    @Test
    void sinRespuestaReintentaMasTardeSinCambiarEstado() {
        pendiente(0);
        when(integra.validar(any(), any()))
                .thenThrow(new ValidacionNoDisponibleException("caída", null));

        srv.validarComprasPendientes();

        verify(repo).registrarIntento(eq("C-2026-000001"), eq(1), eq(Duration.ofSeconds(10)));
        verify(repo, never()).cambiarEstado(any(), any(), any());
        verify(outbox, never()).guardar(any());
    }

    @Test
    void alAgotarLosIntentosPasaAEnRevisionYAvisaLaDemora() {
        pendiente(2);
        when(integra.validar(any(), any())).thenReturn(EstadoOFAC.ENPROCESO);

        srv.validarComprasPendientes();

        verify(repo).registrarIntento(eq("C-2026-000001"), eq(3), isNull());
        verify(repo).cambiarEstado(eq("C-2026-000001"), eq(EstadoCompra.EN_REVISION), any());
        verify(outbox).guardar(any(Notificacion.class));
    }

    @Test
    void pagoRechazadoCompensaYLiberaElBien() {
        when(repo.cambiarEstadoSiActual(
                        "C-2026-000001", EstadoCompra.ESPERANDO_PAGO, EstadoCompra.CANCELADA))
                .thenReturn(true);
        when(repo.obtener("C-2026-000001"))
                .thenReturn(Optional.of(compra(EstadoCompra.CANCELADA, 0)));

        srv.procesarPago(
                "C-2026-000001",
                new ReferenciaPago("YP-1", ResultadoPago.RECHAZADO, BigDecimal.ONE));

        verify(repo).cambiarEstado("C-2026-000001", EstadoCompra.CANCELADA, "pago rechazado");
        verify(outbox).guardar(any(BienLiberado.class));
    }

    @Test
    void otroClienteNoPuedeIniciarLaCompra() {
        when(repo.obtener("C-2026-000001")).thenReturn(Optional.of(compra(EstadoCompra.CREADA, 0)));
        Cliente otro = new Cliente("luis", "999", null, null, null, null, null);

        assertThatThrownBy(() -> srv.iniciarCompra("C-2026-000001", otro))
                .isInstanceOf(CompraAjenaException.class);
        verify(repo, never()).cambiarEstado(any(), any(), any());
    }
}

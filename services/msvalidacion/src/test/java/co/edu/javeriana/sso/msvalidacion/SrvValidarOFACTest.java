package co.edu.javeriana.sso.msvalidacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.msvalidacion.modelo.EstadoOFAC;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SrvValidarOFACTest {

    private final RepoValidar repo = mock(RepoValidar.class);
    private final IntegraOFAC integra = mock(IntegraOFAC.class);
    private final SrvValidarOFAC srv = new SrvValidarOFAC(repo, integra);

    @Test
    void resultadoDefinitivoNoVuelveAConsultarLaOfac() {
        when(repo.buscarResultado("C-1")).thenReturn(Optional.of(EstadoOFAC.RECHAZADO));

        assertThat(srv.validar("C-1", "1010101010")).isEqualTo(EstadoOFAC.RECHAZADO);
        verify(integra, never()).consultar(any());
    }

    @Test
    void enProcesoVuelveAConsultarYGuardaElNuevoResultado() {
        when(repo.buscarResultado("C-1")).thenReturn(Optional.of(EstadoOFAC.ENPROCESO));
        when(integra.consultar("1010101010")).thenReturn(EstadoOFAC.APROBADO);

        assertThat(srv.validar("C-1", "1010101010")).isEqualTo(EstadoOFAC.APROBADO);
        verify(repo).guardarResultado("C-1", EstadoOFAC.APROBADO);
    }
}

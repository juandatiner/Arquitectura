package co.edu.javeriana.sso.mscompra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.mscompra.config.PropiedadesCompra;
import co.edu.javeriana.sso.mscompra.modelo.Compra;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class RepoCompraTest {

    @Test
    @SuppressWarnings("unchecked")
    void tomarPendientesReservaPorElTiempoConfigurado() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec sentencia = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Compra> consulta = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(sentencia);
        when(sentencia.param(anyString(), any())).thenReturn(sentencia);
        when(sentencia.query(any(RowMapper.class))).thenReturn(consulta);
        when(consulta.list()).thenReturn(List.of());
        RepoCompra repo =
                new RepoCompra(
                        jdbc,
                        new PropiedadesCompra(
                                "http://x",
                                20,
                                Duration.ofSeconds(90),
                                3,
                                Duration.ofSeconds(10),
                                Duration.ofMinutes(5),
                                Duration.ofMinutes(30),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1)));

        repo.tomarPendientesValidacion(7);

        verify(sentencia).param("reserva", 90L);
        verify(sentencia).param("lote", 7);
    }
}

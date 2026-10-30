package co.edu.javeriana.sso.ofacbackend;

import co.edu.javeriana.sso.ofacbackend.modelo.EstadoReporte;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB OFAC (PostgreSQL, JDBC). */
@Repository
public class RepoOFAC {

    private final JdbcClient jdbc;

    public RepoOFAC(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso VI.20 (SrvValidacionOFAC → RepoOFAC). */
    public Optional<EstadoReporte> consultarReporte(String cedula) {
        return jdbc.sql("SELECT estado FROM reporte WHERE cedula = :cedula")
                .param("cedula", cedula)
                .query((rs, n) -> EstadoReporte.valueOf(rs.getString("estado")))
                .optional();
    }
}

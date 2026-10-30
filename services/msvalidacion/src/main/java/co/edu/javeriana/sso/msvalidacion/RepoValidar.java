package co.edu.javeriana.sso.msvalidacion;

import co.edu.javeriana.sso.msvalidacion.modelo.EstadoOFAC;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB VALIDAR (PostgreSQL, JDBC). */
@Repository
public class RepoValidar {

    private final JdbcClient jdbc;

    public RepoValidar(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso VI.16 (SrvValidarOFAC → RepoValidar). */
    public Optional<EstadoOFAC> buscarResultado(String numeroCompra) {
        return jdbc.sql("SELECT estado FROM resultado_validacion WHERE numero_compra = :numero")
                .param("numero", numeroCompra)
                .query((rs, n) -> EstadoOFAC.valueOf(rs.getString("estado")))
                .optional();
    }

    /** Paso VI.21 (SrvValidarOFAC → RepoValidar guardarResultado(numeroCompra, estado)). */
    public void guardarResultado(String numeroCompra, EstadoOFAC estado) {
        jdbc.sql(
                        """
                        INSERT INTO resultado_validacion (numero_compra, estado)
                        VALUES (:numero, :estado)
                        ON CONFLICT (numero_compra) DO UPDATE
                           SET estado = EXCLUDED.estado,
                               consultas = resultado_validacion.consultas + 1,
                               actualizado_en = now()
                        """)
                .param("numero", numeroCompra)
                .param("estado", estado.name())
                .update();
    }
}

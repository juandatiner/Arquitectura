package co.edu.javeriana.sso.mssubasta;

import co.edu.javeriana.sso.mssubasta.modelo.EstadoSubasta;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoYVersion;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de subastas en DB SUBASTA (PostgreSQL, JDBC). Las fechas se comparan con la hora de
 * la base.
 */
@Repository
public class RepoSubasta {

    private final JdbcClient jdbc;

    public RepoSubasta(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> buscarPorAbrir(Instant fechaActual) {
        return jdbc.sql(
                        """
                        SELECT id_subasta FROM subasta
                         WHERE estado = 'PROGRAMADA' AND fecha_inicio <= :ahora AND fecha_cierre > :ahora
                         ORDER BY fecha_inicio
                        """)
                .param("ahora", Timestamp.from(fechaActual))
                .query(String.class)
                .list();
    }

    /** Paso III.14. UPDATE condicional: abre solo si está PROGRAMADA y dentro de sus fechas. */
    public boolean abrirSiProgramadaYEnPlazo(String idSubasta) {
        return jdbc.sql(
                                """
                                UPDATE subasta SET estado = 'ABIERTA', version = version + 1
                                 WHERE id_subasta = :id AND estado = 'PROGRAMADA'
                                   AND fecha_inicio <= now() AND fecha_cierre > now()
                                """)
                        .param("id", idSubasta)
                        .update()
                == 1;
    }

    /** Paso IV.9: estado, fechas y versión actual de la subasta. */
    public Optional<EstadoYVersion> obtenerEstadoYVersion(String idSubasta) {
        return jdbc.sql(
                        """
                        SELECT id_subasta, id_bien, descripcion, valor_minimo, fecha_inicio, fecha_cierre,
                               estado, version
                          FROM subasta WHERE id_subasta = :id
                        """)
                .param("id", idSubasta)
                .query(
                        (rs, n) ->
                                new EstadoYVersion(
                                        rs.getString("id_subasta"),
                                        rs.getString("id_bien"),
                                        rs.getString("descripcion"),
                                        rs.getBigDecimal("valor_minimo"),
                                        rs.getTimestamp("fecha_inicio").toInstant(),
                                        rs.getTimestamp("fecha_cierre").toInstant(),
                                        EstadoSubasta.valueOf(rs.getString("estado")),
                                        rs.getLong("version")))
                .optional();
    }

    /**
     * Paso IV.11: control de concurrencia optimista. Solo una de dos ofertas simultáneas leídas con
     * la misma versión logra actualizarla.
     *
     * @return true si filasAfectadas = 1 (IV.12a); false si = 0 (IV.12b)
     */
    public boolean actualizarVersionSiAbiertaYEnPlazo(String idSubasta, long version) {
        return jdbc.sql(
                                """
                                UPDATE subasta SET version = version + 1
                                 WHERE id_subasta = :id AND version = :version AND estado = 'ABIERTA'
                                   AND fecha_inicio <= now() AND fecha_cierre > now()
                                """)
                        .param("id", idSubasta)
                        .param("version", version)
                        .update()
                == 1;
    }

    /** Paso V.2: subastas ABIERTAS (o nunca abiertas) cuya fecha de cierre ya pasó. */
    public List<String> buscarVencidas() {
        return jdbc.sql(
                        """
                        SELECT id_subasta FROM subasta
                         WHERE estado IN ('PROGRAMADA', 'ABIERTA') AND fecha_cierre <= now()
                         ORDER BY fecha_cierre
                        """)
                .query(String.class)
                .list();
    }

    /** Paso V.2: pasa la subasta a EN_CIERRE; desde ese momento ninguna oferta puede entrar. */
    public boolean tomarParaCierre(String idSubasta) {
        return jdbc.sql(
                                """
                                UPDATE subasta SET estado = 'EN_CIERRE', version = version + 1
                                 WHERE id_subasta = :id AND estado IN ('PROGRAMADA', 'ABIERTA')
                                   AND fecha_cierre <= now()
                                """)
                        .param("id", idSubasta)
                        .update()
                == 1;
    }

    /** Paso V.4a: el bien se lo queda quien hizo la mayor oferta. */
    public void adjudicar(String idSubasta, String cedulaGanador, BigDecimal valor) {
        jdbc.sql(
                        """
                        UPDATE subasta SET estado = 'ADJUDICADA', cedula_ganador = :cedula, valor_ganador = :valor
                         WHERE id_subasta = :id AND estado = 'EN_CIERRE'
                        """)
                .param("id", idSubasta)
                .param("cedula", cedulaGanador)
                .param("valor", valor)
                .update();
    }

    /** Paso V.4b: sin ofertas. */
    public void declararDesierta(String idSubasta) {
        jdbc.sql(
                        "UPDATE subasta SET estado = 'DESIERTA' WHERE id_subasta = :id AND estado = 'EN_CIERRE'")
                .param("id", idSubasta)
                .update();
    }

    /** Paso X.7a: idempotente. */
    public boolean liberarSiAdjudicada(String idSubasta) {
        return jdbc.sql(
                                "UPDATE subasta SET estado = 'LIBERADA'"
                                        + " WHERE id_subasta = :id AND estado = 'ADJUDICADA'")
                        .param("id", idSubasta)
                        .update()
                == 1;
    }
}

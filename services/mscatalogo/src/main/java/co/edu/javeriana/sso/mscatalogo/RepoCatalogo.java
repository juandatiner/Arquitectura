package co.edu.javeriana.sso.mscatalogo;

import co.edu.javeriana.sso.mscatalogo.modelo.EstadoSubasta;
import co.edu.javeriana.sso.mscatalogo.modelo.Filtros;
import co.edu.javeriana.sso.mscatalogo.modelo.PaginaCatalogo;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaPublicada;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB CATALOGO (PostgreSQL, JDBC). */
@Repository
public class RepoCatalogo {

    private static final String SELECT =
            """
            SELECT s.id_subasta, s.id_bien, b.descripcion, b.categoria, b.valor_minimo,
                   s.fecha_inicio, s.fecha_cierre, s.estado
              FROM subasta s JOIN bien b ON b.id_bien = s.id_bien
            """;
    private static final String FILTRO =
            """
             WHERE s.estado = 'PUBLICADA'
               AND (CAST(:texto AS TEXT) IS NULL OR b.descripcion ILIKE '%' || :texto || '%')
               AND (CAST(:categoria AS TEXT) IS NULL OR b.categoria = :categoria)
            """;

    private final JdbcClient jdbc;
    private final Clock reloj;

    public RepoCatalogo(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Paso II.9a (SrvCatalogo → RepoCatalogo). */
    public PaginaCatalogo buscarSubastasPublicadas(Filtros filtros) {
        String texto =
                filtros.texto() == null || filtros.texto().isBlank()
                        ? null
                        : filtros.texto().trim();
        String categoria =
                filtros.categoria() == null || filtros.categoria().isBlank()
                        ? null
                        : filtros.categoria();
        List<SubastaPublicada> contenido =
                jdbc.sql(SELECT + FILTRO + " ORDER BY s.fecha_cierre LIMIT :tamano OFFSET :desde")
                        .param("texto", texto)
                        .param("categoria", categoria)
                        .param("tamano", filtros.tamanoEfectivo())
                        .param("desde", filtros.paginaEfectiva() * filtros.tamanoEfectivo())
                        .query(RepoCatalogo::fila)
                        .list();
        long total =
                jdbc.sql(
                                "SELECT count(*) FROM subasta s JOIN bien b ON b.id_bien = s.id_bien"
                                        + FILTRO)
                        .param("texto", texto)
                        .param("categoria", categoria)
                        .query(Long.class)
                        .single();
        return new PaginaCatalogo(
                contenido, filtros.paginaEfectiva(), filtros.tamanoEfectivo(), total);
    }

    /**
     * Paso III.9 (SrvCatalogo → RepoCatalogo). UPDATE condicional: solo publica si está PENDIENTE y
     * su fecha de cierre no ha pasado. La fecha actual sale del reloj. {@code publicadaPor} no está
     * en el paso: observación 21.
     *
     * @return true si la publicó (filasAfectadas = 1)
     */
    public boolean publicarSiPendienteYEnFecha(String idSubasta, String publicadaPor) {
        return jdbc.sql(
                                """
                                UPDATE subasta
                                   SET estado = 'PUBLICADA', publicada_por = :por, publicada_en = :ahora
                                 WHERE id_subasta = :id AND estado = 'PENDIENTE' AND fecha_cierre > :ahora
                                """)
                        .param("id", idSubasta)
                        .param("por", publicadaPor)
                        .param("ahora", Timestamp.from(Instant.now(reloj)))
                        .update()
                == 1;
    }

    /**
     * Paso X.7b (SrvCatalogo → RepoCatalogo). Marca la subasta LIBERADA cuando la compra se
     * compensa: sale del catálogo y el bien vuelve a ofertarse con una subasta nueva (observación
     * 16). Idempotente.
     *
     * @return true si cambió el estado (filasAfectadas = 1)
     */
    public boolean liberarSiAdjudicada(String idSubasta) {
        return jdbc.sql(
                                "UPDATE subasta SET estado = 'LIBERADA'"
                                        + " WHERE id_subasta = :id AND estado = 'PUBLICADA'")
                        .param("id", idSubasta)
                        .update()
                == 1;
    }

    public Optional<SubastaPublicada> obtener(String idSubasta) {
        return jdbc.sql(SELECT + " WHERE s.id_subasta = :id")
                .param("id", idSubasta)
                .query(RepoCatalogo::fila)
                .optional();
    }

    private static SubastaPublicada fila(ResultSet rs, int n) throws SQLException {
        return new SubastaPublicada(
                rs.getString("id_subasta"),
                rs.getString("id_bien"),
                rs.getString("descripcion"),
                rs.getString("categoria"),
                rs.getBigDecimal("valor_minimo"),
                rs.getTimestamp("fecha_inicio").toInstant(),
                rs.getTimestamp("fecha_cierre").toInstant(),
                EstadoSubasta.valueOf(rs.getString("estado")));
    }
}

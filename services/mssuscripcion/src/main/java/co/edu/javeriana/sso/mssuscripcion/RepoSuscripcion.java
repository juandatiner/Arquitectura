package co.edu.javeriana.sso.mssuscripcion;

import co.edu.javeriana.sso.mssuscripcion.modelo.Cliente;
import co.edu.javeriana.sso.mssuscripcion.modelo.CriterioSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.EstadoSuscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.Suscripcion;
import co.edu.javeriana.sso.mssuscripcion.modelo.TipoSuscripcion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB Suscripcion (PostgreSQL, JDBC). */
@Repository
public class RepoSuscripcion {

    private static final String COLUMNAS =
            "id_suscripcion, usuario, cedula, nombre, celular, correo, tipo, valor, estado, creada_en";

    private final JdbcClient jdbc;

    public RepoSuscripcion(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso II.9b. Idempotente: si ya hay una suscripción ACTIVA igual, la devuelve. */
    public Suscripcion activarSuscripcion(CriterioSuscripcion criterio, Cliente cliente) {
        jdbc.sql(
                        """
                        INSERT INTO suscripcion (id_suscripcion, usuario, cedula, nombre, correo, celular, tipo, valor, estado)
                        VALUES (:id, :usuario, :cedula, :nombre, :correo, :celular, :tipo, :valor, 'ACTIVA')
                        ON CONFLICT (cedula, tipo, valor) WHERE estado = 'ACTIVA' DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("usuario", cliente.usuario())
                .param("cedula", cliente.cedula())
                .param("nombre", cliente.nombre())
                .param("correo", cliente.correo())
                .param("celular", cliente.celular())
                .param("tipo", criterio.tipo().name())
                .param("valor", criterio.valor())
                .update();
        return jdbc.sql(
                        "SELECT "
                                + COLUMNAS
                                + " FROM suscripcion WHERE cedula = :cedula AND tipo = :tipo"
                                + " AND valor = :valor AND estado = 'ACTIVA'")
                .param("cedula", cliente.cedula())
                .param("tipo", criterio.tipo().name())
                .param("valor", criterio.valor())
                .query(RepoSuscripcion::fila)
                .single();
    }

    /** Paso II.9b. Solo cancela si la suscripción está ACTIVA y es de ese cliente. */
    public Optional<Suscripcion> cancelarSuscripcion(UUID idSuscripcion, String cedula) {
        return jdbc.sql(
                        "UPDATE suscripcion SET estado = 'CANCELADA', cancelada_en = now()"
                                + " WHERE id_suscripcion = :id AND cedula = :cedula AND estado = 'ACTIVA'"
                                + " RETURNING "
                                + COLUMNAS)
                .param("id", idSuscripcion)
                .param("cedula", cedula)
                .query(RepoSuscripcion::fila)
                .optional();
    }

    /**
     * Pasos III.20 / IV.18. Registra el evento como procesado en la misma transacción de sus
     * notificaciones.
     *
     * @return true si el evento ya se había procesado antes (duplicado)
     */
    public boolean yaProcesado(UUID idEvento) {
        return jdbc.sql(
                                "INSERT INTO evento_procesado (id_evento) VALUES (:id) ON CONFLICT DO NOTHING")
                        .param("id", idEvento)
                        .update()
                == 0;
    }

    /** Pasos III.20 / IV.18: suscripciones ACTIVAS de un tipo para alguno de los valores. */
    public List<Suscripcion> buscarSuscriptores(TipoSuscripcion tipo, List<String> valores) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNAS
                                + " FROM suscripcion WHERE estado = 'ACTIVA' AND tipo = :tipo"
                                + " AND valor IN (:valores) ORDER BY creada_en")
                .param("tipo", tipo.name())
                .param("valores", valores)
                .query(RepoSuscripcion::fila)
                .list();
    }

    /** Guarda la subasta conocida por los eventos (su categoría sirve para SubastaIniciada). */
    public void registrarSubasta(
            String idSubasta, String descripcion, String categoria, String estado) {
        jdbc.sql(
                        """
                        INSERT INTO subasta_conocida (id_subasta, descripcion, categoria, estado)
                        VALUES (:id, :descripcion, :categoria, :estado)
                        ON CONFLICT (id_subasta) DO UPDATE
                           SET descripcion = EXCLUDED.descripcion,
                               categoria = COALESCE(EXCLUDED.categoria, subasta_conocida.categoria),
                               estado = EXCLUDED.estado
                        """)
                .param("id", idSubasta)
                .param("descripcion", descripcion)
                .param("categoria", categoria)
                .param("estado", estado)
                .update();
    }

    public Optional<String> categoriaDe(String idSubasta) {
        return jdbc.sql("SELECT categoria FROM subasta_conocida WHERE id_subasta = :id")
                .param("id", idSubasta)
                .query(String.class)
                .optional();
    }

    /**
     * Paso X.7c: la subasta vuelve a estar disponible para los suscriptores. Idempotente.
     *
     * @return true si cambió el estado
     */
    public boolean liberarSiAdjudicada(String idSubasta) {
        return jdbc.sql(
                                "UPDATE subasta_conocida SET estado = 'LIBERADA'"
                                        + " WHERE id_subasta = :id AND estado <> 'LIBERADA'")
                        .param("id", idSubasta)
                        .update()
                == 1;
    }

    private static Suscripcion fila(ResultSet rs, int n) throws SQLException {
        return new Suscripcion(
                rs.getObject("id_suscripcion", UUID.class),
                new Cliente(
                        rs.getString("usuario"),
                        rs.getString("cedula"),
                        rs.getString("nombre"),
                        rs.getString("celular"),
                        rs.getString("correo"),
                        null),
                TipoSuscripcion.valueOf(rs.getString("tipo")),
                rs.getString("valor"),
                EstadoSuscripcion.valueOf(rs.getString("estado")),
                rs.getTimestamp("creada_en").toInstant());
    }
}

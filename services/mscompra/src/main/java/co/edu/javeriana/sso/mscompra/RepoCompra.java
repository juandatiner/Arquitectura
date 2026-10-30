package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.modelo.Cliente;
import co.edu.javeriana.sso.mscompra.modelo.Compra;
import co.edu.javeriana.sso.mscompra.modelo.EstadoCompra;
import co.edu.javeriana.sso.mscompra.modelo.SubastaCerrada;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB COMPRA (PostgreSQL, JDBC), compartido por las dos réplicas. */
@Repository
public class RepoCompra {

    private static final String COLUMNAS =
            """
            numero_compra, id_subasta, id_bien, descripcion, usuario, cedula, nombre, celular, correo,
            direccion, valor_ofertado, estado, motivo, intentos_ofac, creada_en
            """;

    private final JdbcClient jdbc;

    public RepoCompra(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso V.10a/b. */
    public boolean existePorSubasta(String idSubasta) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM compra WHERE id_subasta = :id)")
                .param("id", idSubasta)
                .query(Boolean.class)
                .single();
    }

    /** Paso V.10a/b: crear(Compra) en estado CREADA con el ganador de la subasta. */
    public Compra crear(SubastaCerrada evento) {
        Cliente g = evento.ganador();
        return jdbc.sql(
                        "INSERT INTO compra (numero_compra, id_subasta, id_bien, descripcion, usuario, cedula,"
                                + " nombre, celular, correo, valor_ofertado, estado)"
                                + " VALUES ('C-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('compra_numero')::text, 6, '0'),"
                                + " :idSubasta, :idBien, :descripcion, :usuario, :cedula, :nombre, :celular, :correo,"
                                + " :valor, 'CREADA') RETURNING "
                                + COLUMNAS)
                .param("idSubasta", evento.idSubasta())
                .param("idBien", evento.idBien())
                .param("descripcion", evento.descripcion())
                .param("usuario", g.usuario())
                .param("cedula", g.cedula())
                .param("nombre", g.nombre())
                .param("celular", g.celular())
                .param("correo", g.correo())
                .param("valor", evento.valor())
                .query(RepoCompra::fila)
                .single();
    }

    /** Paso VI.10. */
    public Optional<Compra> obtener(String numeroCompra) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM compra WHERE numero_compra = :numero")
                .param("numero", numeroCompra)
                .query(RepoCompra::fila)
                .optional();
    }

    /** VI.1 consultarMisCompras (observación 6). */
    public List<Compra> consultarCompras(String cedula) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNAS
                                + " FROM compra WHERE cedula = :cedula ORDER BY creada_en DESC")
                .param("cedula", cedula)
                .query(RepoCompra::fila)
                .list();
    }

    /** Dirección registrada del cliente (del token) para programar el envío (VIII.4). */
    public void registrarDireccion(String numeroCompra, String direccion) {
        jdbc.sql("UPDATE compra SET direccion = :direccion WHERE numero_compra = :numero")
                .param("numero", numeroCompra)
                .param("direccion", direccion)
                .update();
    }

    /** Pasos VI.10, VI.22a, VI.22b, VI.22c y X.1. Libera la reserva de validación. */
    public void cambiarEstado(String numeroCompra, EstadoCompra estado, String motivo) {
        jdbc.sql(
                        """
                        UPDATE compra SET estado = :estado, motivo = :motivo, reservada_hasta = NULL,
                               proximo_intento = CASE WHEN :estado = 'EN_VALIDACION' THEN now() ELSE proximo_intento END,
                               actualizada_en = now()
                         WHERE numero_compra = :numero
                        """)
                .param("numero", numeroCompra)
                .param("estado", estado.name())
                .param("motivo", motivo)
                .update();
    }

    /**
     * Pasos VII.32a, VIII.23 y X.9: transición condicional. Si otra réplica o un evento repetido ya
     * la hizo, no cambia nada.
     *
     * @return true si filasAfectadas = 1
     */
    public boolean cambiarEstadoSiActual(
            String numeroCompra, EstadoCompra actual, EstadoCompra nuevo) {
        return jdbc.sql(
                                """
                                UPDATE compra SET estado = :nuevo, actualizada_en = now(),
                                       despacho_solicitado_en = CASE WHEN :nuevo = 'PAGO_CONFIRMADO' THEN now()
                                                                     ELSE despacho_solicitado_en END
                                 WHERE numero_compra = :numero AND estado = :actual
                                """)
                        .param("numero", numeroCompra)
                        .param("actual", actual.name())
                        .param("nuevo", nuevo.name())
                        .update()
                == 1;
    }

    public void registrarPago(String numeroCompra, String referenciaPago) {
        jdbc.sql("UPDATE compra SET referencia_pago = :referencia WHERE numero_compra = :numero")
                .param("numero", numeroCompra)
                .param("referencia", referenciaPago)
                .update();
    }

    /**
     * Paso VI.12: reserva un lote de compras EN_VALIDACION cuyo próximo intento ya llegó. La
     * reserva ({@code reservada_hasta}) y SKIP LOCKED evitan que las dos réplicas validen la misma
     * compra.
     */
    public List<Compra> tomarPendientesValidacion(int tamañoLote, Duration reserva) {
        return jdbc.sql(
                        "UPDATE compra SET reservada_hasta = now() + make_interval(secs => :reserva)"
                                + " WHERE numero_compra IN (SELECT numero_compra FROM compra"
                                + "   WHERE estado = 'EN_VALIDACION' AND proximo_intento <= now()"
                                + "     AND (reservada_hasta IS NULL OR reservada_hasta < now())"
                                + "   ORDER BY proximo_intento LIMIT :lote FOR UPDATE SKIP LOCKED)"
                                + " RETURNING "
                                + COLUMNAS)
                .param("reserva", reserva.toSeconds())
                .param("lote", tamañoLote)
                .query(RepoCompra::fila)
                .list();
    }

    /**
     * Paso VI.22c: registrarIntento(intento + 1) y cuándo volver a consultar (null = no
     * reintentar).
     */
    public void registrarIntento(String numeroCompra, int intento, Duration espera) {
        jdbc.sql(
                        """
                        UPDATE compra SET intentos_ofac = :intento, reservada_hasta = NULL, actualizada_en = now(),
                               proximo_intento = CASE WHEN CAST(:espera AS BIGINT) IS NULL THEN NULL
                                                      ELSE now() + make_interval(secs => CAST(:espera AS BIGINT)) END
                         WHERE numero_compra = :numero
                        """)
                .param("numero", numeroCompra)
                .param("intento", intento)
                .param("espera", espera == null ? null : espera.toSeconds())
                .update();
    }

    /** Compras con pago confirmado cuyo despacho no se confirmó dentro del plazo (X.9). */
    public List<String> buscarDespachosAgotados(Duration plazo) {
        return jdbc.sql(
                        "SELECT numero_compra FROM compra WHERE estado = 'PAGO_CONFIRMADO'"
                                + " AND despacho_solicitado_en < now() - make_interval(secs => :plazo)")
                .param("plazo", plazo.toSeconds())
                .query(String.class)
                .list();
    }

    private static Compra fila(ResultSet rs, int n) throws SQLException {
        return new Compra(
                rs.getString("numero_compra"),
                rs.getString("id_subasta"),
                rs.getString("id_bien"),
                rs.getString("descripcion"),
                new Cliente(
                        rs.getString("usuario"),
                        rs.getString("cedula"),
                        rs.getString("nombre"),
                        rs.getString("celular"),
                        rs.getString("correo"),
                        rs.getString("direccion"),
                        null),
                rs.getBigDecimal("valor_ofertado"),
                EstadoCompra.valueOf(rs.getString("estado")),
                rs.getString("motivo"),
                rs.getInt("intentos_ofac"),
                rs.getTimestamp("creada_en").toInstant());
    }
}

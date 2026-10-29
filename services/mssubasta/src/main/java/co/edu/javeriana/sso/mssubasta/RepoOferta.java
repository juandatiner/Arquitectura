package co.edu.javeriana.sso.mssubasta;

import co.edu.javeriana.sso.mssubasta.modelo.Cliente;
import co.edu.javeriana.sso.mssubasta.modelo.EstadoOferta;
import co.edu.javeriana.sso.mssubasta.modelo.Oferta;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Repositorio de ofertas en DB SUBASTA (PostgreSQL, JDBC). */
@Repository
public class RepoOferta {

    private final JdbcClient jdbc;

    public RepoOferta(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Pasos IV.10 y V.3. */
    public Optional<Oferta> obtenerOfertaMayor(String idSubasta) {
        return jdbc.sql(
                        """
                        SELECT id_oferta, id_subasta, usuario, cedula, nombre, celular, correo, valor, estado
                          FROM oferta WHERE id_subasta = :id
                         ORDER BY valor DESC, id_oferta
                         LIMIT 1
                        """)
                .param("id", idSubasta)
                .query(
                        (rs, n) ->
                                new Oferta(
                                        rs.getLong("id_oferta"),
                                        rs.getString("id_subasta"),
                                        new Cliente(
                                                rs.getString("usuario"),
                                                rs.getString("cedula"),
                                                rs.getString("nombre"),
                                                rs.getString("celular"),
                                                rs.getString("correo"),
                                                null),
                                        rs.getBigDecimal("valor"),
                                        EstadoOferta.valueOf(rs.getString("estado"))))
                .optional();
    }

    /** Paso IV.12a: la nueva oferta queda REGISTRADA y la anterior pasa a SUPERADA. */
    public long guardarOferta(String idSubasta, Cliente cliente, BigDecimal valor) {
        jdbc.sql(
                        "UPDATE oferta SET estado = 'SUPERADA' WHERE id_subasta = :id AND estado = 'REGISTRADA'")
                .param("id", idSubasta)
                .update();
        GeneratedKeyHolder clave = new GeneratedKeyHolder();
        jdbc.sql(
                        """
                        INSERT INTO oferta (id_subasta, usuario, cedula, nombre, celular, correo, valor, estado)
                        VALUES (:id, :usuario, :cedula, :nombre, :celular, :correo, :valor, 'REGISTRADA')
                        """)
                .param("id", idSubasta)
                .param("usuario", cliente.usuario())
                .param("cedula", cliente.cedula())
                .param("nombre", cliente.nombre())
                .param("celular", cliente.celular())
                .param("correo", cliente.correo())
                .param("valor", valor)
                .update(clave, "id_oferta");
        return clave.getKey().longValue();
    }

    /** Paso V.3: la oferta ganadora queda GANADORA y todas las demás PERDEDORA. */
    public void marcarResultado(String idSubasta, Long idOfertaGanadora) {
        jdbc.sql(
                        """
                        UPDATE oferta
                           SET estado = CASE WHEN id_oferta = :ganadora THEN 'GANADORA' ELSE 'PERDEDORA' END
                         WHERE id_subasta = :id
                        """)
                .param("id", idSubasta)
                .param("ganadora", idOfertaGanadora == null ? -1L : idOfertaGanadora)
                .update();
    }
}

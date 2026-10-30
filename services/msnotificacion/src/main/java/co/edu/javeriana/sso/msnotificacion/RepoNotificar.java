package co.edu.javeriana.sso.msnotificacion;

import co.edu.javeriana.sso.msnotificacion.modelo.Canal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB NOTIFICACION (PostgreSQL, JDBC). */
@Repository
public class RepoNotificar {

    private final JdbcClient jdbc;

    public RepoNotificar(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso IX.3: canales por los que este evento ya se envió. */
    public Set<Canal> yaEnviada(UUID idEvento) {
        Set<Canal> canales = EnumSet.noneOf(Canal.class);
        jdbc.sql("SELECT canal FROM notificacion_enviada WHERE id_evento = :id")
                .param("id", idEvento)
                .query((rs, n) -> Canal.valueOf(rs.getString("canal")))
                .list()
                .forEach(canales::add);
        return canales;
    }

    /** Paso IX.8: registrarEnviada(idEvento, canal, resultado). Idempotente. */
    public void registrarEnviada(UUID idEvento, Canal canal, String resultado) {
        jdbc.sql(
                        """
                        INSERT INTO notificacion_enviada (id_evento, canal, resultado)
                        VALUES (:id, :canal, :resultado)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", idEvento)
                .param("canal", canal.name())
                .param("resultado", resultado)
                .update();
    }
}

package co.edu.javeriana.sso.mscompra;

import co.edu.javeriana.sso.mscompra.modelo.Evento;
import co.edu.javeriana.sso.mscompra.modelo.EventoPendiente;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Outbox transaccional en DB COMPRA (ADR 0002). */
@Repository
public class RepoOutboxCompra {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public RepoOutboxCompra(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Pasos V.11a/b, VI.23a/b/c, VII.33a, VIII.24, X.2 y X.10 (guardar). Se ejecuta dentro de la
     * transacción de SrvCompra: el evento existe solo si el cambio de negocio se confirmó.
     */
    public void guardar(Evento evento) {
        jdbc.sql(
                        """
                        INSERT INTO outbox (id_evento, tipo, cola, payload, estado)
                        VALUES (:id, :tipo, :cola, CAST(:payload AS JSON), :estado)
                        """)
                .param("id", evento.idEvento())
                .param("tipo", evento.tipo())
                .param("cola", evento.cola())
                .param("payload", json.writeValueAsString(evento))
                .param("estado", evento.cola() == null ? "REGISTRADO" : "PENDIENTE")
                .update();
    }

    /**
     * Pasos V.12a/b, VII.1, VIII.1 y X.3 (WorkerOutboxCompra → RepoOutboxCompra). Bloquea el lote
     * con SKIP LOCKED para que dos instancias nunca publiquen el mismo evento.
     */
    public List<EventoPendiente> obtenerPendientes(int tamañoLote) {
        return jdbc.sql(
                        """
                        SELECT id_evento, tipo, cola, payload::text AS payload
                          FROM outbox
                         WHERE estado = 'PENDIENTE'
                         ORDER BY creado_en
                         LIMIT :lote
                           FOR UPDATE SKIP LOCKED
                        """)
                .param("lote", tamañoLote)
                .query(
                        (rs, n) ->
                                new EventoPendiente(
                                        rs.getObject("id_evento", UUID.class),
                                        rs.getString("tipo"),
                                        rs.getString("cola"),
                                        rs.getString("payload")))
                .list();
    }

    public void marcarEnviado(UUID idEvento) {
        jdbc.sql("UPDATE outbox SET estado = 'ENVIADO', enviado_en = now() WHERE id_evento = :id")
                .param("id", idEvento)
                .update();
    }

    public void registrarIntento(UUID idEvento) {
        jdbc.sql("UPDATE outbox SET intentos = intentos + 1 WHERE id_evento = :id")
                .param("id", idEvento)
                .update();
    }
}

package co.edu.javeriana.sso.senbackend;

import co.edu.javeriana.sso.senbackend.modelo.Evento;
import co.edu.javeriana.sso.senbackend.modelo.EventoPendiente;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Outbox transaccional (ADR 0002). */
@Repository
public class RepoOutboxSEN {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public RepoOutboxSEN(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Paso VIII.12b (SrvEnvio → RepoOutboxSEN guardar(EnvioConfirmado)). Se ejecuta dentro de la
     * transacción del servicio: el evento existe solo si el cambio de negocio se confirmó.
     */
    public void guardar(Evento evento) {
        jdbc.sql(
                        """
                        INSERT INTO outbox (id_evento, tipo, cola, payload, estado)
                        VALUES (:id, :tipo, :cola, CAST(:payload AS JSON), :estado)
                        """)
                .param("id", evento.idEvento())
                .param("tipo", evento.getClass().getSimpleName())
                .param("cola", evento.cola())
                .param("payload", json.writeValueAsString(evento))
                .param("estado", evento.cola() == null ? "REGISTRADO" : "PENDIENTE")
                .update();
    }

    /**
     * Paso VIII.13b (WorkerOutboxSEN → RepoOutboxSEN). Bloquea el lote con SKIP LOCKED para que dos
     * instancias nunca publiquen el mismo evento.
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

using System.Text.Json;
using Dapper;
using MSPago.config;
using MSPago.modelo;

namespace MSPago;

/// <summary>Outbox transaccional en DB PAGO (ADR 0002).</summary>
public class RepoOutboxPago(UnidadDeTrabajo unidad)
{
    /// <summary>Pasos VII.11 (guardar(NotificacionPago)) y VII.27 (guardar(PagoConfirmado)), en la transacción del servicio.</summary>
    public virtual async Task guardar(IEvento evento) =>
        await (await unidad.Conexion()).ExecuteAsync(
            "INSERT INTO outbox (id_evento, tipo, cola, payload, estado) VALUES (@id, @tipo, @cola, CAST(@payload AS JSON), 'PENDIENTE')",
            new { id = evento.IdEvento, tipo = evento.Tipo, cola = evento.Cola, payload = JsonSerializer.Serialize(evento, evento.GetType(), Json.Opciones) },
            unidad.Transaccion);

    /// <summary>Pasos VII.12 y VII.28 (WorkerOutboxPago → RepoOutboxPago obtenerPendientes(tamañoLote)), con SKIP LOCKED.</summary>
    public virtual async Task<IReadOnlyList<EventoPendiente>> obtenerPendientes(int tamañoLote) =>
        (await (await unidad.Conexion()).QueryAsync<EventoPendiente>(
            """
            SELECT id_evento AS IdEvento, tipo AS Tipo, cola AS Cola, payload::text AS Payload
              FROM outbox WHERE estado = 'PENDIENTE' ORDER BY creado_en LIMIT @tamañoLote FOR UPDATE SKIP LOCKED
            """, new { tamañoLote }, unidad.Transaccion)).ToList();

    public virtual async Task marcarEnviado(Guid idEvento) =>
        await (await unidad.Conexion()).ExecuteAsync(
            "UPDATE outbox SET estado = 'ENVIADO', enviado_en = now() WHERE id_evento = @idEvento", new { idEvento }, unidad.Transaccion);

    public virtual async Task registrarIntento(Guid idEvento) =>
        await (await unidad.Conexion()).ExecuteAsync(
            "UPDATE outbox SET intentos = intentos + 1 WHERE id_evento = @idEvento", new { idEvento }, unidad.Transaccion);
}

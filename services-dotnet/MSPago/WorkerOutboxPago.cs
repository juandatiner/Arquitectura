using System.Text;
using Microsoft.Extensions.Options;
using MSPago.config;
using MSPago.modelo;
using RabbitMQ.Client;

namespace MSPago;

/// <summary>Publica en RabbitMQ los eventos del outbox. Si MOM no está, quedan PENDIENTES y salen cuando vuelva.</summary>
public sealed class WorkerOutboxPago(
    ConexionMom mom, IServiceScopeFactory alcances, IOptions<OpcionesPago> opciones, ILogger<WorkerOutboxPago> log)
    : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var cancelacion = stoppingToken;
        IChannel? canal = null;
        while (!cancelacion.IsCancellationRequested)
        {
            try
            {
                if (canal is null || canal.IsClosed)
                {
                    var conexion = await mom.Obtener(cancelacion);
                    canal = await conexion.CreateChannelAsync(
                        new CreateChannelOptions(publisherConfirmationsEnabled: true, publisherConfirmationTrackingEnabled: true),
                        cancelacion);
                }
                await publicarPendientes(canal, cancelacion);
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                log.LogWarning("MOM no disponible; los eventos quedan pendientes: {Mensaje}", e.Message);
                canal = null;
            }
            await Task.Delay(opciones.Value.IntervaloOutbox, cancelacion);
        }
    }

    /// <summary>
    /// Pasos VII.12–VII.13 (→ q.notificacion publicar(NotificacionPago)) y VII.28–VII.29 (→ q.pagoConfirmado
    /// publicar(PagoConfirmado), .NET → Java). Espera la confirmación del broker antes de marcar el evento.
    /// </summary>
    private async Task publicarPendientes(IChannel canal, CancellationToken cancelacion)
    {
        await using var alcance = alcances.CreateAsyncScope();
        var repo = alcance.ServiceProvider.GetRequiredService<RepoOutboxPago>();
        var unidad = alcance.ServiceProvider.GetRequiredService<UnidadDeTrabajo>();
        foreach (var evento in await repo.obtenerPendientes(opciones.Value.TamanoLote))
        {
            try
            {
                var propiedades = new BasicProperties
                {
                    ContentType = "application/json",
                    ContentEncoding = "UTF-8",
                    MessageId = evento.IdEvento.ToString(),
                    Type = evento.Tipo,
                    DeliveryMode = DeliveryModes.Persistent,
                };
                await canal.BasicPublishAsync(evento.Cola, "", false, propiedades, Encoding.UTF8.GetBytes(evento.Payload), cancelacion);
                await repo.marcarEnviado(evento.IdEvento);
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                await repo.registrarIntento(evento.IdEvento);
                await unidad.Confirmar();
                throw;
            }
        }
        await unidad.Confirmar();
    }
}

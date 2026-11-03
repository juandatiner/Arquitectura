using System.Text.Json;
using MSPago.config;
using MSPago.modelo;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;

namespace MSPago;

/// <summary>Consumidor de q.pagoSolicitud (lo publica MSCompra en Java: integración Java → .NET por AMQP).</summary>
public sealed class ListenerPagoSolicitud(ConexionMom mom, IServiceScopeFactory alcances, ILogger<ListenerPagoSolicitud> log)
    : BackgroundService
{
    private const string Cola = "q.pagoSolicitud";

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var cancelacion = stoppingToken;
        var conexion = await mom.Obtener(cancelacion);
        var canal = await conexion.CreateChannelAsync(cancellationToken: cancelacion);
        await canal.BasicQosAsync(0, 5, false, cancelacion);
        var consumidor = new AsyncEventingBasicConsumer(canal);
        consumidor.ReceivedAsync += async (_, entrega) =>
        {
            try
            {
                await entregar(JsonSerializer.Deserialize<PagoSolicitud>(entrega.Body.Span, Json.Opciones)!);
                await canal.BasicAckAsync(entrega.DeliveryTag, false, cancelacion);
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                // Se devuelve a la cola tras una espera; la cola quorum lo pasa a q.pagoSolicitud.dlq al quinto intento.
                log.LogWarning("Pago no preparado ({Mensaje}); se reintenta", e.Message);
                await Task.Delay(TimeSpan.FromSeconds(5), cancelacion);
                await canal.BasicNackAsync(entrega.DeliveryTag, false, true, cancelacion);
            }
        };
        await canal.BasicConsumeAsync(Cola, autoAck: false, consumer: consumidor, cancellationToken: cancelacion);
        await Task.Delay(Timeout.Infinite, cancelacion);
    }

    /// <summary>Pasos VII.3 (q.pagoSolicitud → entregar(solicitud)) y VII.4 (→ SrvPago prepararPago).</summary>
    public async Task entregar(PagoSolicitud solicitud)
    {
        await using var alcance = alcances.CreateAsyncScope();
        var srvPago = alcance.ServiceProvider.GetRequiredService<SrvPago>();
        await srvPago.prepararPago(solicitud.NumeroCompra, solicitud.Cedula, solicitud.ValorOfertado, solicitud.Destinatario);
    }
}

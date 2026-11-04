using System.Text;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using MSPago;
using RabbitMQ.Client;
using Testcontainers.PostgreSql;
using Testcontainers.RabbitMq;
using Xunit;

namespace SSO.Dotnet.Tests;

/// <summary>
/// Java → .NET → Java con PostgreSQL y RabbitMQ reales: el correlationId de q.pagoSolicitud llega a la llamada HTTP a
/// YoPago (VII.7) y, por el outbox, a la cabecera AMQP de q.notificacion (VII.13).
/// </summary>
public sealed class MSPagoAmqpIT : IAsyncLifetime
{
    private readonly PostgreSqlContainer db = new PostgreSqlBuilder().WithImage("postgres:17-alpine").Build();
    private readonly RabbitMqContainer mom = new RabbitMqBuilder().WithImage("rabbitmq:4.1-management-alpine").Build();

    public Task InitializeAsync() => Task.WhenAll(db.StartAsync(), mom.StartAsync());

    public async Task DisposeAsync()
    {
        await db.DisposeAsync();
        await mom.DisposeAsync();
    }

    [Fact]
    public async Task ElCorrelationIdCruzaDeLaColaAYoPagoYAlEventoSiguiente()
    {
        var conexionDb = new Npgsql.NpgsqlConnectionStringBuilder(db.GetConnectionString());
        Environment.SetEnvironmentVariable("DB_HOST", $"{conexionDb.Host}:{conexionDb.Port}");
        Environment.SetEnvironmentVariable("DB_NOMBRE", conexionDb.Database);
        Environment.SetEnvironmentVariable("DB_USUARIO", conexionDb.Username);
        Environment.SetEnvironmentVariable("DB_CLAVE", conexionDb.Password);
        Environment.SetEnvironmentVariable("TASA_IMPUESTO", "0.19");
        Environment.SetEnvironmentVariable("RUTA_YOPAGO", "http://sin-uso:5001");
        Environment.SetEnvironmentVariable("URL_PAGO", "http://sin-uso:5000");
        Environment.SetEnvironmentVariable("MOM_HOST", "reemplazado-en-la-prueba");
        Environment.SetEnvironmentVariable("MOM_USUARIO", "x");
        Environment.SetEnvironmentVariable("MOM_CLAVE", "x");

        var fabricaMom = new ConnectionFactory { Uri = new Uri(mom.GetConnectionString()) };
        await using var conexion = await fabricaMom.CreateConnectionAsync();
        await using var canal = await conexion.CreateChannelAsync();
        await canal.QueueDeclareAsync("q.pagoSolicitud", durable: true, exclusive: false, autoDelete: false);
        await canal.ExchangeDeclareAsync("q.notificacion", ExchangeType.Fanout, durable: true);
        await canal.QueueDeclareAsync("q.notificacion", durable: true, exclusive: false, autoDelete: false);
        await canal.QueueBindAsync("q.notificacion", "q.notificacion", "");

        string? recibidoEnYoPago = null;
        await using var fabrica = new WebApplicationFactory<SrvPago>().WithWebHostBuilder(b => b.ConfigureTestServices(s =>
        {
            s.AddSingleton<IConnectionFactory>(fabricaMom);
            s.ConfigureHttpClientDefaults(http => http.ConfigurePrimaryHttpMessageHandler(() => new Grabador(valor => recibidoEnYoPago = valor)));
        }));
        _ = fabrica.Server;

        // Igual que WorkerOutboxCompra (Java): cabecera AMQP de texto y el cuerpo sin correlationId
        var propiedades = new BasicProperties
        {
            Type = "PagoSolicitud",
            Headers = new Dictionary<string, object?> { ["correlationId"] = "desde-java-1" },
        };
        var cuerpo = """{"idEvento":"0b6f2f0e-4c7f-4d55-9b0a-1f6a0a2f7c11","numeroCompra":"C-1","cedula":"1010","valorOfertado":1000,"destinatario":{"nombre":"Ana","celular":"300"}}""";
        await canal.BasicPublishAsync("", "q.pagoSolicitud", false, propiedades, Encoding.UTF8.GetBytes(cuerpo));

        BasicGetResult? notificacion = null;
        for (var i = 0; i < 60 && notificacion is null; i++)
        {
            await Task.Delay(500);
            notificacion = await canal.BasicGetAsync("q.notificacion", autoAck: true);
        }

        Assert.NotNull(notificacion);
        Assert.Equal("desde-java-1", recibidoEnYoPago);
        Assert.Equal("desde-java-1", Encoding.UTF8.GetString((byte[])notificacion.BasicProperties.Headers!["correlationId"]!));
        Assert.DoesNotContain("desde-java-1", Encoding.UTF8.GetString(notificacion.Body.Span), StringComparison.Ordinal);
    }
}

using DbUp;
using Npgsql;
using RabbitMQ.Client;

namespace MSPago.config;

/// <summary>Configuración de MSPago. Todo llega por variables de entorno (deploy/maquina-3/.env); sin valores por defecto.</summary>
public sealed class OpcionesPago
{
    /// <summary>Tasa de impuestos sobre el valor ofertado (enunciado: "el valor que ofreció en la subasta más impuestos").</summary>
    public decimal TasaImpuesto { get; init; }

    /// <summary>WSCompraPendiente · YoPago SERVER :5001 (flecha HTTP/REST de VII.7).</summary>
    public required string UrlYoPago { get; init; }

    /// <summary>Sitio de pago YoPagoWeb SERVER :5000 que se envía al cliente en el SMS.</summary>
    public required string UrlPago { get; init; }

    public TimeSpan IntervaloOutbox { get; init; } = TimeSpan.FromSeconds(1);
    public int TamanoLote { get; init; } = 50;
    public TimeSpan TimeoutYoPago { get; init; } = TimeSpan.FromSeconds(5);
}

public static class Entorno
{
    public static string Requerida(string nombre) =>
        Environment.GetEnvironmentVariable(nombre) is { Length: > 0 } valor
            ? valor
            : throw new InvalidOperationException($"Falta la variable de entorno {nombre}");
}

/// <summary>Una transacción de DB PAGO por operación: los repositorios la comparten (cambio + outbox juntos).</summary>
public sealed class UnidadDeTrabajo(NpgsqlDataSource fuente) : IAsyncDisposable
{
    private NpgsqlConnection? conexion;

    public NpgsqlTransaction? Transaccion { get; private set; }

    public async Task<NpgsqlConnection> Conexion()
    {
        if (conexion is null)
        {
            conexion = await fuente.OpenConnectionAsync();
            Transaccion = await conexion.BeginTransactionAsync();
        }
        return conexion;
    }

    public async Task Confirmar()
    {
        if (Transaccion is not null)
        {
            await Transaccion.CommitAsync();
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (Transaccion is not null)
        {
            await Transaccion.DisposeAsync();
        }
        if (conexion is not null)
        {
            await conexion.DisposeAsync();
        }
    }
}

/// <summary>Conexión compartida con MOM SERVER :5672 (RabbitMQ), con recuperación automática.</summary>
public sealed class ConexionMom(IConnectionFactory fabrica, ILogger<ConexionMom> log) : IAsyncDisposable
{
    private readonly SemaphoreSlim candado = new(1, 1);
    private IConnection? conexion;

    public async Task<IConnection> Obtener(CancellationToken cancelacion)
    {
        await candado.WaitAsync(cancelacion);
        try
        {
            while (conexion is null || !conexion.IsOpen)
            {
                try
                {
                    conexion = await fabrica.CreateConnectionAsync(cancelacion);
                }
                catch (Exception e) when (e is not OperationCanceledException)
                {
                    log.LogWarning("MOM no disponible: {Mensaje}. Reintento en 5 s", e.Message);
                    await Task.Delay(TimeSpan.FromSeconds(5), cancelacion);
                }
            }
            return conexion;
        }
        finally
        {
            candado.Release();
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (conexion is not null)
        {
            await conexion.DisposeAsync();
        }
        candado.Dispose();
    }
}

public static class Migraciones
{
    /// <summary>Aplica los scripts SQL versionados de Migrations/ (como Flyway en los componentes Java).</summary>
    public static void Aplicar(string cadena)
    {
        var resultado = DeployChanges.To.PostgresqlDatabase(cadena)
            .WithScriptsEmbeddedInAssembly(typeof(Migraciones).Assembly)
            .JournalToPostgresqlTable("public", "schemaversions")
            .LogToConsole()
            .Build()
            .PerformUpgrade();
        if (!resultado.Successful)
        {
            throw new InvalidOperationException("No se pudo migrar DB PAGO", resultado.Error);
        }
    }
}

public static class ManejoErrores
{
    /// <summary>Errores como ProblemDetails (RFC 9457), igual que los componentes Java.</summary>
    public static IResult? Traducir(Exception e) => e switch
    {
        modelo.PagoNoEncontradoException => Results.Problem(e.Message, statusCode: StatusCodes.Status404NotFound),
        _ => null,
    };
}

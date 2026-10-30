using System.Security.Cryptography;
using DbUp;
using Npgsql;
using YoPagoBackEnd.modelo;

namespace YoPagoBackEnd.config;

public static class Entorno
{
    public static string Requerida(string nombre) =>
        Environment.GetEnvironmentVariable(nombre) is { Length: > 0 } valor
            ? valor
            : throw new InvalidOperationException($"Falta la variable de entorno {nombre}");
}

/// <summary>Una transacción de DB YOPAGO por operación.</summary>
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
            await Transaccion.DisposeAsync();
            Transaccion = await conexion!.BeginTransactionAsync();
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

/// <summary>Claves con PBKDF2-SHA256: formato pbkdf2_sha256$iteraciones$sal$hash (base64), igual que tools/carga/cargar_yopago.py.</summary>
public static class Claves
{
    public static bool Verificar(string clave, string almacenada)
    {
        var partes = almacenada.Split('$');
        if (partes.Length != 4 || partes[0] != "pbkdf2_sha256")
        {
            return false;
        }
        var sal = Convert.FromBase64String(partes[2]);
        var esperado = Convert.FromBase64String(partes[3]);
        var calculado = Rfc2898DeriveBytes.Pbkdf2(clave, sal, int.Parse(partes[1]), HashAlgorithmName.SHA256, esperado.Length);
        return CryptographicOperations.FixedTimeEquals(calculado, esperado);
    }
}

public static class Migraciones
{
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
            throw new InvalidOperationException("No se pudo migrar DB YOPAGO", resultado.Error);
        }
    }
}

public static class ManejoErrores
{
    public static IResult? Traducir(Exception e) => e switch
    {
        CredencialesInvalidasException => Results.Problem(e.Message, statusCode: StatusCodes.Status401Unauthorized),
        CompraNoEncontradaException => Results.Problem(e.Message, statusCode: StatusCodes.Status404NotFound),
        CompraYaPagadaException => Results.Problem(e.Message, statusCode: StatusCodes.Status409Conflict),
        _ => null,
    };
}

/// <summary>Reintenta en segundo plano los pagos que no se pudieron reportar a SSO (caída de red o de SSO).</summary>
public sealed class ReintentoReportes(IServiceScopeFactory alcances, ILogger<ReintentoReportes> log) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await using var alcance = alcances.CreateAsyncScope();
                await alcance.ServiceProvider.GetRequiredService<SrvYoPago>().reintentarReportes();
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                log.LogWarning("Reintento de reportes falló: {Mensaje}", e.Message);
            }
            await Task.Delay(TimeSpan.FromSeconds(15), stoppingToken);
        }
    }
}

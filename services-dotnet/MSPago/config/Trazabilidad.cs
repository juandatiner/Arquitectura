using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Routing;
using Serilog;
using Serilog.Context;
using Serilog.Events;
using Serilog.Formatting.Compact;

namespace MSPago.config;

/// <summary>
/// correlationId de la petición o del evento en curso: viaja en la cabecera HTTP X-Correlation-Id y en la cabecera
/// AMQP correlationId, nunca en los cuerpos JSON de los pasos, y sale en cada línea de log.
/// </summary>
public static class Correlacion
{
    public const string CabeceraHttp = "X-Correlation-Id";
    public const string CabeceraAmqp = "correlationId";

    /// <summary>Lo recibido de afuera se valida: evita inyectar texto en los logs.</summary>
    private static readonly Regex Valido = new(@"^[A-Za-z0-9._-]{8,64}\z", RegexOptions.CultureInvariant);

    private static readonly AsyncLocal<string?> Actual = new();

    /// <summary>El correlationId en curso o, si este flujo no tiene ninguno, uno nuevo.</summary>
    public static string ActualOGenerar() => Normalizar(Actual.Value);

    /// <summary>El valor recibido si es válido; si falta o no es válido, uno nuevo. AMQP entrega los textos como bytes.</summary>
    public static string Normalizar(object? recibido)
    {
        var texto = recibido switch
        {
            string s => s,
            byte[] b => Encoding.UTF8.GetString(b),
            _ => null,
        };
        return texto is not null && Valido.IsMatch(texto) ? texto : Guid.NewGuid().ToString();
    }

    /// <summary>Deja el correlationId en el flujo actual y en los logs hasta liberar el resultado.</summary>
    public static IDisposable Abrir(string correlationId)
    {
        var anterior = Actual.Value;
        Actual.Value = correlationId;
        return new Ambito(anterior, LogContext.PushProperty("correlationId", correlationId));
    }

    private sealed class Ambito(string? anterior, IDisposable propiedad) : IDisposable
    {
        public void Dispose()
        {
            propiedad.Dispose();
            Actual.Value = anterior;
        }
    }
}

/// <summary>Toda llamada HTTP saliente lleva el correlationId en curso.</summary>
public sealed class PropagarCorrelacionHttp : DelegatingHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        request.Headers.Remove(Correlacion.CabeceraHttp);
        request.Headers.Add(Correlacion.CabeceraHttp, Correlacion.ActualOGenerar());
        return base.SendAsync(request, cancellationToken);
    }
}

public static class Trazabilidad
{
    /// <summary>Logs JSON en consola (con correlationId como campo propio) y propagación en todos los HttpClient.</summary>
    public static void AgregarTrazabilidad(this WebApplicationBuilder builder)
    {
        builder.Host.UseSerilog((_, logs) => logs
            .MinimumLevel.Information()
            .MinimumLevel.Override("Microsoft.AspNetCore", LogEventLevel.Warning)
            // Estos registran URLs completas, que pueden llevar datos del cliente en la consulta
            .MinimumLevel.Override("System.Net.Http", LogEventLevel.Warning)
            .Enrich.FromLogContext()
            .WriteTo.Console(new RenderedCompactJsonFormatter()));
        builder.Services.AddTransient<PropagarCorrelacionHttp>();
        builder.Services.ConfigureHttpClientDefaults(http => http.AddHttpMessageHandler<PropagarCorrelacionHttp>());
    }

    /// <summary>
    /// Toma el correlationId de la petición (o crea uno) y lo devuelve en la respuesta. Registra la plantilla de la
    /// ruta, no la URL: así los datos del cliente en una ruta o una consulta no llegan a los logs.
    /// </summary>
    public static void UsarTrazabilidad(this IApplicationBuilder app)
    {
        var log = app.ApplicationServices.GetRequiredService<ILoggerFactory>().CreateLogger(typeof(Trazabilidad));
        app.Use(async (contexto, siguiente) =>
        {
            var correlationId = Correlacion.Normalizar(contexto.Request.Headers[Correlacion.CabeceraHttp].ToString());
            contexto.Response.Headers[Correlacion.CabeceraHttp] = correlationId;
            var inicio = Stopwatch.GetTimestamp();
            using var ambito = Correlacion.Abrir(correlationId);
            try
            {
                await siguiente();
            }
            finally
            {
                if (!contexto.Request.Path.StartsWithSegments("/health") && log.IsEnabled(LogLevel.Information))
                {
                    log.LogInformation(
                        "peticion atendida {Metodo} {Ruta} {Estado} {DuracionMs}",
                        contexto.Request.Method,
                        (contexto.GetEndpoint() as RouteEndpoint)?.RoutePattern.RawText ?? "sin-ruta",
                        contexto.Response.StatusCode,
                        (long)Stopwatch.GetElapsedTime(inicio).TotalMilliseconds);
                }
            }
        });
    }
}

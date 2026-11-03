using MSPago;
using MSPago.config;
using Npgsql;
using RabbitMQ.Client;

// Componente MSPago.dll · nodo MSPago SERVER :5086 (Kestrel .NET 8) · Máquina 3 · 10.43.99.45
var builder = WebApplication.CreateBuilder(args);

var cadena = new NpgsqlConnectionStringBuilder
{
    Host = Entorno.Requerida("DB_HOST"),
    Database = Entorno.Requerida("DB_NOMBRE"),
    Username = Entorno.Requerida("DB_USUARIO"),
    Password = Entorno.Requerida("DB_CLAVE"),
}.ConnectionString;

builder.Services.AddSingleton(_ => Microsoft.Extensions.Options.Options.Create(new OpcionesPago
{
    TasaImpuesto = decimal.Parse(Entorno.Requerida("TASA_IMPUESTO"), System.Globalization.CultureInfo.InvariantCulture),
    UrlYoPago = Entorno.Requerida("RUTA_YOPAGO"),
    UrlPago = Entorno.Requerida("URL_PAGO"),
}));
builder.Services.AddSingleton(NpgsqlDataSource.Create(cadena));
builder.Services.AddScoped<UnidadDeTrabajo>();
builder.Services.AddScoped<RepoPago>();
builder.Services.AddScoped<RepoOutboxPago>();
builder.Services.AddScoped<SrvPago>();
builder.Services.AddHttpClient<IntegraYoPago>(http =>
{
    http.BaseAddress = new Uri(Entorno.Requerida("RUTA_YOPAGO").TrimEnd('/') + "/");
    http.Timeout = TimeSpan.FromSeconds(5);
});
builder.Services.AddSingleton<IConnectionFactory>(new ConnectionFactory
{
    HostName = Entorno.Requerida("MOM_HOST"),
    UserName = Entorno.Requerida("MOM_USUARIO"),
    Password = Entorno.Requerida("MOM_CLAVE"),
    AutomaticRecoveryEnabled = true,
    ClientProvidedName = "MSPago",
});
builder.Services.AddSingleton<ConexionMom>();
builder.Services.AddHostedService<ListenerPagoSolicitud>();
builder.Services.AddHostedService<WorkerOutboxPago>();
builder.Services.AddControllers();
builder.Services.AddProblemDetails();

var app = builder.Build();
Migraciones.Aplicar(cadena);
app.UseExceptionHandler(errores => errores.Run(async contexto =>
{
    var e = contexto.Features.Get<Microsoft.AspNetCore.Diagnostics.IExceptionHandlerFeature>()?.Error;
    var resultado = e is null ? null : ManejoErrores.Traducir(e);
    await (resultado ?? Results.Problem("Error interno", statusCode: 500)).ExecuteAsync(contexto);
}));
app.MapControllers();
app.MapGet("/health", () => Results.Ok(new { status = "UP" }));
app.Run();

public partial class Program;

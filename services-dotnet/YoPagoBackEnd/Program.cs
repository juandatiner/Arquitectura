using System.Security.Cryptography.X509Certificates;
using Npgsql;
using YoPagoBackEnd;
using YoPagoBackEnd.config;

// Componente YoPagoBackEnd.dll · nodo YoPago SERVER :5001 (Kestrel .NET 8) · Máquina 3 · sistema externo
var builder = WebApplication.CreateBuilder(args);

var cadena = new NpgsqlConnectionStringBuilder
{
    Host = Entorno.Requerida("DB_HOST"),
    Database = Entorno.Requerida("DB_NOMBRE"),
    Username = Entorno.Requerida("DB_USUARIO"),
    Password = Entorno.Requerida("DB_CLAVE"),
}.ConnectionString;

// HTTPS hacia SSO (VII.20) confiando solo en el certificado del balanceador (certificado fijado, sin desactivar la validación).
using var certificadoSso = new X509Certificate2(Entorno.Requerida("CERTIFICADO_SSO"));
var credencial = Entorno.Requerida("CREDENCIAL_YOPAGO");

builder.Services.AddSingleton(NpgsqlDataSource.Create(cadena));
builder.Services.AddScoped<UnidadDeTrabajo>();
builder.Services.AddScoped<RepoYoPago>();
builder.Services.AddScoped<SrvYoPago>();
builder.Services.AddHttpClient("sso", http =>
    {
        http.BaseAddress = new Uri(Entorno.Requerida("RUTA_SSO").TrimEnd('/') + "/");
        http.Timeout = TimeSpan.FromSeconds(10);
    })
    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler
    {
        ServerCertificateCustomValidationCallback = (_, certificado, _, _) =>
            certificado is not null && certificado.RawData.AsSpan().SequenceEqual(certificadoSso.RawData),
    });
builder.Services.AddScoped(s => new IntegraSSO(s.GetRequiredService<IHttpClientFactory>().CreateClient("sso"), credencial));
builder.Services.AddHostedService<ReintentoReportes>();
builder.Services.AddControllers();
builder.Services.AddProblemDetails();

var app = builder.Build();
Migraciones.Aplicar(cadena);
app.UseExceptionHandler(errores => errores.Run(async contexto =>
{
    var e = contexto.Features.Get<Microsoft.AspNetCore.Diagnostics.IExceptionHandlerFeature>()?.Error;
    await (( e is null ? null : ManejoErrores.Traducir(e)) ?? Results.Problem("Error interno", statusCode: 500)).ExecuteAsync(contexto);
}));
app.MapControllers();
app.MapGet("/health", () => Results.Ok(new { status = "UP" }));
app.Run();

public partial class Program;

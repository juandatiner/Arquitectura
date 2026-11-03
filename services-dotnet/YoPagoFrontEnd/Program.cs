using Microsoft.AspNetCore.Mvc;
using YoPagoFrontEnd;

// Componente YoPagoFrontEnd.dll · nodo YoPagoWeb SERVER :5000 (Kestrel .NET 8) · Máquina 1 · 10.43.99.2 (DMZ)
var builder = WebApplication.CreateBuilder(args);

var rutaYoPago = Environment.GetEnvironmentVariable("RUTA_YOPAGO") is { Length: > 0 } ruta
    ? ruta
    : throw new InvalidOperationException("Falta la variable de entorno RUTA_YOPAGO");

builder.Services.AddControllersWithViews(o => o.Filters.Add(new AutoValidateAntiforgeryTokenAttribute()));
builder.Services.AddHttpClient<ProxyWSYoPagoWeb>(http =>
{
    http.BaseAddress = new Uri(rutaYoPago.TrimEnd('/') + "/");
    http.Timeout = TimeSpan.FromSeconds(10);
});
builder.Services.AddDistributedMemoryCache();
builder.Services.AddSession(o =>
{
    o.IdleTimeout = TimeSpan.FromMinutes(10);
    o.Cookie.HttpOnly = true;
    o.Cookie.SameSite = SameSiteMode.Strict;
    o.Cookie.Name = "yopago.sesion";
});

var app = builder.Build();
app.UseStaticFiles();
app.UseSession();
app.MapControllers();
app.MapGet("/health", () => Results.Ok(new { status = "UP" }));
app.Run();

public partial class Program;

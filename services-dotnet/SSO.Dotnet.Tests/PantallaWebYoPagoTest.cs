using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using NSubstitute;
using Xunit;
using YoPagoFrontEnd;
using YoPagoFrontEnd.modelo;

namespace SSO.Dotnet.Tests;

/// <summary>Los formularios de la pantalla YoPago llevan el token antiforgery que exige AutoValidateAntiforgeryToken.</summary>
public sealed partial class PantallaWebYoPagoTest : IDisposable
{
    private readonly ProxyWSYoPagoWeb proxy = Substitute.For<ProxyWSYoPagoWeb>(new HttpClient());
    private readonly WebApplicationFactory<PantallaWebYoPago> fabrica;

    public PantallaWebYoPagoTest()
    {
        Environment.SetEnvironmentVariable("RUTA_YOPAGO", "http://sin-uso:5001");
        fabrica = new WebApplicationFactory<PantallaWebYoPago>().WithWebHostBuilder(b =>
            b.ConfigureTestServices(s => s.AddTransient(_ => proxy)));
    }

    [Fact]
    public async Task IngresaConElTokenDelFormularioYMuestraLaCompraPendiente()
    {
        proxy.consultarCompraPendiente("1010", "Pago2026*", null)
            .Returns([new CompraPendiente("C-1", "1010", 1190m, "PENDIENTE", null, null, null)]);
        using var cliente = fabrica.CreateClient();

        var formulario = await cliente.GetStringAsync("/?compra=C-1");
        using var respuesta = await cliente.PostAsync("/consultar", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = Token(formulario),
            ["cedula"] = "1010",
            ["clave"] = "Pago2026*",
            ["compra"] = "C-1",
        }));

        respuesta.EnsureSuccessStatusCode();
        var pendientes = await respuesta.Content.ReadAsStringAsync();
        Assert.Contains("Compra C-1", pendientes, StringComparison.Ordinal);
        Assert.NotEmpty(Token(pendientes));
    }

    [Fact]
    public async Task RechazaElPostSinToken()
    {
        using var cliente = fabrica.CreateClient();

        using var respuesta = await cliente.PostAsync("/consultar", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["cedula"] = "1010",
            ["clave"] = "Pago2026*",
        }));

        Assert.Equal(400, (int)respuesta.StatusCode);
        await proxy.DidNotReceiveWithAnyArgs().consultarCompraPendiente(default!, default!, default);
    }

    public void Dispose() => fabrica.Dispose();

    private static string Token(string html) => CampoToken().Match(html).Groups[1].Value;

    [GeneratedRegex("name=\"__RequestVerificationToken\" type=\"hidden\" value=\"([^\"]+)\"")]
    private static partial Regex CampoToken();
}

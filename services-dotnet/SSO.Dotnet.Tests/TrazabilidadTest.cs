using System.Net;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Xunit;
using NMSPago = MSPago.config;
using NYoPagoBackEnd = YoPagoBackEnd.config;
using NYoPagoFrontEnd = YoPagoFrontEnd.config;

namespace SSO.Dotnet.Tests;

/// <summary>Destino HTTP que registra el correlationId que le llega.</summary>
internal sealed class Grabador(Action<string?>? alRecibir = null) : HttpMessageHandler
{
    public string? Recibido { get; private set; }

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Recibido = request.Headers.TryGetValues("X-Correlation-Id", out var valores) ? valores.Single() : null;
        alRecibir?.Invoke(Recibido);
        return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK));
    }
}

public sealed class TrazabilidadMSPagoTest
{
    private const string Id = "abc-12345";

    private static async Task<(string? Visto, string? Respuesta)> Atender(string? recibido)
    {
        string? visto = null;
        using var anfitrion = await new HostBuilder().ConfigureWebHost(w => w.UseTestServer()
            .ConfigureServices(s => { s.AddLogging(); s.AddRouting(); })
            .Configure(app =>
            {
                NMSPago.Trazabilidad.UsarTrazabilidad(app);
                app.Run(_ =>
                {
                    visto = NMSPago.Correlacion.ActualOGenerar();
                    return Task.CompletedTask;
                });
            })).StartAsync();
        using var peticion = new HttpRequestMessage(HttpMethod.Get, "/x");
        if (recibido is not null)
        {
            peticion.Headers.TryAddWithoutValidation(NMSPago.Correlacion.CabeceraHttp, recibido);
        }
        using var respuesta = await anfitrion.GetTestClient().SendAsync(peticion);
        return (visto, respuesta.Headers.GetValues(NMSPago.Correlacion.CabeceraHttp).Single());
    }

    [Fact]
    public async Task ConservaElCorrelationIdRecibido()
    {
        var (visto, respuesta) = await Atender(Id);
        Assert.Equal(Id, visto);
        Assert.Equal(Id, respuesta);
    }

    [Fact]
    public async Task CreaUnoSiNoLlega()
    {
        var (visto, respuesta) = await Atender(null);
        Assert.False(string.IsNullOrWhiteSpace(visto));
        Assert.Equal(visto, respuesta);
    }

    [Fact]
    public async Task ReemplazaElQueNoEsValido()
    {
        var (visto, _) = await Atender("x\"} falso");
        Assert.DoesNotContain("falso", visto, StringComparison.Ordinal);
    }

    [Fact]
    public void AceptaLosBytesConQueAmqpEntregaLosTextos() =>
        Assert.Equal(Id, NMSPago.Correlacion.Normalizar(System.Text.Encoding.UTF8.GetBytes(Id)));

    [Fact]
    public async Task LasLlamadasSalientesLlevanElCorrelationIdEnCurso()
    {
        using var destino = new Grabador();
        using var manejador = new NMSPago.PropagarCorrelacionHttp { InnerHandler = destino };
        using var cliente = new HttpClient(manejador);

        using (NMSPago.Correlacion.Abrir(Id))
        {
            using var respuesta = await cliente.GetAsync(new Uri("http://destino/x"));
        }

        Assert.Equal(Id, destino.Recibido);
    }
}

public sealed class TrazabilidadYoPagoBackEndTest
{
    private const string Id = "abc-12345";

    private static async Task<(string? Visto, string? Respuesta)> Atender(string? recibido)
    {
        string? visto = null;
        using var anfitrion = await new HostBuilder().ConfigureWebHost(w => w.UseTestServer()
            .ConfigureServices(s => { s.AddLogging(); s.AddRouting(); })
            .Configure(app =>
            {
                NYoPagoBackEnd.Trazabilidad.UsarTrazabilidad(app);
                app.Run(_ =>
                {
                    visto = NYoPagoBackEnd.Correlacion.ActualOGenerar();
                    return Task.CompletedTask;
                });
            })).StartAsync();
        using var peticion = new HttpRequestMessage(HttpMethod.Get, "/x");
        if (recibido is not null)
        {
            peticion.Headers.TryAddWithoutValidation(NYoPagoBackEnd.Correlacion.CabeceraHttp, recibido);
        }
        using var respuesta = await anfitrion.GetTestClient().SendAsync(peticion);
        return (visto, respuesta.Headers.GetValues(NYoPagoBackEnd.Correlacion.CabeceraHttp).Single());
    }

    [Fact]
    public async Task ConservaElCorrelationIdRecibido()
    {
        var (visto, respuesta) = await Atender(Id);
        Assert.Equal(Id, visto);
        Assert.Equal(Id, respuesta);
    }

    [Fact]
    public async Task CreaUnoSiNoLlega()
    {
        var (visto, respuesta) = await Atender(null);
        Assert.False(string.IsNullOrWhiteSpace(visto));
        Assert.Equal(visto, respuesta);
    }

    [Fact]
    public async Task ReemplazaElQueNoEsValido()
    {
        var (visto, _) = await Atender("x\"} falso");
        Assert.DoesNotContain("falso", visto, StringComparison.Ordinal);
    }

    [Fact]
    public void AceptaLosBytesConQueAmqpEntregaLosTextos() =>
        Assert.Equal(Id, NYoPagoBackEnd.Correlacion.Normalizar(System.Text.Encoding.UTF8.GetBytes(Id)));

    [Fact]
    public async Task LasLlamadasSalientesLlevanElCorrelationIdEnCurso()
    {
        using var destino = new Grabador();
        using var manejador = new NYoPagoBackEnd.PropagarCorrelacionHttp { InnerHandler = destino };
        using var cliente = new HttpClient(manejador);

        using (NYoPagoBackEnd.Correlacion.Abrir(Id))
        {
            using var respuesta = await cliente.GetAsync(new Uri("http://destino/x"));
        }

        Assert.Equal(Id, destino.Recibido);
    }
}

public sealed class TrazabilidadYoPagoFrontEndTest
{
    private const string Id = "abc-12345";

    private static async Task<(string? Visto, string? Respuesta)> Atender(string? recibido)
    {
        string? visto = null;
        using var anfitrion = await new HostBuilder().ConfigureWebHost(w => w.UseTestServer()
            .ConfigureServices(s => { s.AddLogging(); s.AddRouting(); })
            .Configure(app =>
            {
                NYoPagoFrontEnd.Trazabilidad.UsarTrazabilidad(app);
                app.Run(_ =>
                {
                    visto = NYoPagoFrontEnd.Correlacion.ActualOGenerar();
                    return Task.CompletedTask;
                });
            })).StartAsync();
        using var peticion = new HttpRequestMessage(HttpMethod.Get, "/x");
        if (recibido is not null)
        {
            peticion.Headers.TryAddWithoutValidation(NYoPagoFrontEnd.Correlacion.CabeceraHttp, recibido);
        }
        using var respuesta = await anfitrion.GetTestClient().SendAsync(peticion);
        return (visto, respuesta.Headers.GetValues(NYoPagoFrontEnd.Correlacion.CabeceraHttp).Single());
    }

    [Fact]
    public async Task ConservaElCorrelationIdRecibido()
    {
        var (visto, respuesta) = await Atender(Id);
        Assert.Equal(Id, visto);
        Assert.Equal(Id, respuesta);
    }

    [Fact]
    public async Task CreaUnoSiNoLlega()
    {
        var (visto, respuesta) = await Atender(null);
        Assert.False(string.IsNullOrWhiteSpace(visto));
        Assert.Equal(visto, respuesta);
    }

    [Fact]
    public async Task ReemplazaElQueNoEsValido()
    {
        var (visto, _) = await Atender("x\"} falso");
        Assert.DoesNotContain("falso", visto, StringComparison.Ordinal);
    }

    [Fact]
    public void AceptaLosBytesConQueAmqpEntregaLosTextos() =>
        Assert.Equal(Id, NYoPagoFrontEnd.Correlacion.Normalizar(System.Text.Encoding.UTF8.GetBytes(Id)));

    [Fact]
    public async Task LasLlamadasSalientesLlevanElCorrelationIdEnCurso()
    {
        using var destino = new Grabador();
        using var manejador = new NYoPagoFrontEnd.PropagarCorrelacionHttp { InnerHandler = destino };
        using var cliente = new HttpClient(manejador);

        using (NYoPagoFrontEnd.Correlacion.Abrir(Id))
        {
            using var respuesta = await cliente.GetAsync(new Uri("http://destino/x"));
        }

        Assert.Equal(Id, destino.Recibido);
    }
}

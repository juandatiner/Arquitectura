using Microsoft.Extensions.Options;
using MSPago;
using MSPago.config;
using MSPago.modelo;
using Npgsql;
using NSubstitute;
using Xunit;

namespace SSO.Dotnet.Tests;

public sealed class SrvPagoTest
{
    private readonly RepoPago repoPago;
    private readonly RepoOutboxPago repoOutbox;
    private readonly IntegraYoPago integraYoPago;
    private readonly SrvPago srvPago;

    public SrvPagoTest()
    {
        var unidad = new UnidadDeTrabajo(NpgsqlDataSource.Create("Host=sin-uso"));
        repoPago = Substitute.For<RepoPago>(unidad);
        repoOutbox = Substitute.For<RepoOutboxPago>(unidad);
        integraYoPago = Substitute.For<IntegraYoPago>(new HttpClient());
        srvPago = new SrvPago(repoPago, repoOutbox, integraYoPago, unidad,
            Options.Create(new OpcionesPago { TasaImpuesto = 0.19m, UrlYoPago = "http://yopago", UrlPago = "http://10.43.99.2:5000" }));
    }

    [Fact]
    public async Task PrepararPagoRegistraEnYoPagoElValorConImpuestosYAvisaPorSms()
    {
        await srvPago.prepararPago("C-1", "1010", 100_000_000m, new Destinatario("Ana", "a@x.co", "3001110001"));

        await integraYoPago.Received(1).registrarPendiente("1010", "C-1", 119_000_000m);
        await repoPago.Received(1).guardar(new Pago("C-1", "1010", 100_000_000m, 19_000_000m, 119_000_000m, "PENDIENTE"));
        await repoOutbox.Received(1).guardar(Arg.Is<IEvento>(e =>
            e is NotificacionPago && ((NotificacionPago)e).Sms
            && ((NotificacionPago)e).Cuerpo.Contains("119.000.000", StringComparison.Ordinal)
            && ((NotificacionPago)e).Cuerpo.Contains("http://10.43.99.2:5000/?compra=C-1", StringComparison.Ordinal)));
    }

    [Fact]
    public async Task SolicitudRepetidaNoRegistraOtraVez()
    {
        repoPago.existe("C-1").Returns(true);

        await srvPago.prepararPago("C-1", "1010", 100m, null);

        await integraYoPago.DidNotReceiveWithAnyArgs().registrarPendiente(default!, default!, default);
        await repoPago.DidNotReceiveWithAnyArgs().guardar(default!);
    }

    [Fact]
    public async Task ConciliaAprobadoSoloSiCedulaYValorCoinciden()
    {
        repoPago.obtenerPorCompra("C-1").Returns(new Pago("C-1", "1010", 1000m, 190m, 1190m, "PENDIENTE"));
        repoPago.confirmarSiPendiente("C-1", Arg.Any<string>(), Arg.Any<decimal>(), Arg.Any<string?>()).Returns(true);

        var aprobado = await srvPago.conciliarPago("1010", "C-1", 1190m);
        var rechazado = await srvPago.conciliarPago("1010", "C-1", 1000m);

        Assert.Equal("APROBADO", aprobado.Resultado);
        Assert.NotNull(aprobado.ReferenciaPago);
        Assert.Equal("RECHAZADO", rechazado.Resultado);
        await repoOutbox.Received(1).guardar(Arg.Is<IEvento>(e => e is PagoConfirmado && ((PagoConfirmado)e).Resultado == "APROBADO"));
        await repoOutbox.Received(1).guardar(Arg.Is<IEvento>(e => e is PagoConfirmado && ((PagoConfirmado)e).Resultado == "RECHAZADO"));
    }

    [Fact]
    public async Task ReporteRepetidoNoGeneraOtroEvento()
    {
        repoPago.obtenerPorCompra("C-1").Returns(new Pago("C-1", "1010", 1000m, 190m, 1190m, "PAGADO"));

        var resultado = await srvPago.conciliarPago("1010", "C-1", 1190m);

        Assert.Equal("APROBADO", resultado.Resultado);
        await repoOutbox.DidNotReceiveWithAnyArgs().guardar(default!);
    }
}

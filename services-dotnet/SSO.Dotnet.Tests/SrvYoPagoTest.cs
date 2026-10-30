using Microsoft.Extensions.Logging.Abstractions;
using Npgsql;
using NSubstitute;
using NSubstitute.ExceptionExtensions;
using Xunit;
using YoPagoBackEnd;
using YoPagoBackEnd.config;
using YoPagoBackEnd.modelo;

namespace SSO.Dotnet.Tests;

public sealed class SrvYoPagoTest
{
    /// <summary>Hash generado por tools/carga/cargar_yopago.py (Python) para la clave Pago2026*.</summary>
    private const string HashPython = "pbkdf2_sha256$100000$c2FsdGRlcHJ1ZWJhMTIzNA==$VW0u5K1bwR4qIZIKpVMXXa91R7Cfu5FeqQXDyGwaDb0=";

    private readonly RepoYoPago repo;
    private readonly IntegraSSO integraSSO;
    private readonly SrvYoPago srv;

    public SrvYoPagoTest()
    {
        var unidad = new UnidadDeTrabajo(NpgsqlDataSource.Create("Host=sin-uso"));
        repo = Substitute.For<RepoYoPago>(unidad);
        integraSSO = Substitute.For<IntegraSSO>(new HttpClient(), "credencial");
        srv = new SrvYoPago(repo, integraSSO, unidad, NullLogger<SrvYoPago>.Instance);
        repo.claveDe("1010").Returns(HashPython);
        repo.buscarPendiente("C-1").Returns(new CompraPendiente("C-1", "1010", 1190m, "PENDIENTE", null, null, null));
    }

    [Fact]
    public void LaClaveCifradaEnPythonSeVerificaEnDotnet()
    {
        Assert.True(Claves.Verificar("Pago2026*", HashPython));
        Assert.False(Claves.Verificar("otra", HashPython));
    }

    [Fact]
    public async Task ClaveIncorrectaNoPermitePagar()
    {
        await Assert.ThrowsAsync<CredencialesInvalidasException>(() => srv.pagar("C-1", new DatosPago("1010", "mala", "PSE", "Ana")));
        await repo.DidNotReceiveWithAnyArgs().registrarPagoConfirmado(default!, default!, default!);
    }

    [Fact]
    public async Task PagaYReportaASso()
    {
        integraSSO.reportarPago("1010", "C-1", 1190m).Returns(new RespuestaSso("C-1", "APROBADO", "PAGO-1"));

        var resultado = await srv.pagar("C-1", new DatosPago("1010", "Pago2026*", "PSE", "Ana"));

        Assert.Equal("PAGADA", resultado.Estado);
        Assert.Equal("REPORTADO", resultado.Reporte);
        await repo.Received(1).registrarPagoConfirmado("C-1", "PSE", Arg.Any<string>());
        await repo.Received(1).registrarReporte("C-1", "APROBADO");
    }

    [Fact]
    public async Task SiSsoNoRespondeElPagoQuedaHechoYElReportePendiente()
    {
        integraSSO.reportarPago("1010", "C-1", 1190m).ThrowsAsync(new SsoNoDisponibleException("caído"));

        var resultado = await srv.pagar("C-1", new DatosPago("1010", "Pago2026*", "PSE", "Ana"));

        Assert.Equal("PAGADA", resultado.Estado);
        Assert.Equal("PENDIENTE", resultado.Reporte);
        await repo.Received(1).registrarIntentoReporte("C-1");
    }

    [Fact]
    public async Task OtroClienteNoPuedePagarLaCompra()
    {
        repo.claveDe("2020").Returns(HashPython);
        await Assert.ThrowsAsync<CompraNoEncontradaException>(() => srv.pagar("C-1", new DatosPago("2020", "Pago2026*", "PSE", "Luis")));
    }
}

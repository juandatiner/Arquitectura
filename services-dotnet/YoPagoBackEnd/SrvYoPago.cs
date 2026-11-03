using YoPagoBackEnd.config;
using YoPagoBackEnd.modelo;

namespace YoPagoBackEnd;

/// <summary>Lógica de YoPago: compras SSO pendientes, pago y reporte a SSO.</summary>
public class SrvYoPago(RepoYoPago repoYoPago, IntegraSSO integraSSO, UnidadDeTrabajo unidad, ILogger<SrvYoPago> log)
{
    /// <summary>Pasos VII.8 (WSCompraPendiente → SrvYoPago) y VII.9 (→ RepoYoPago guardarPendiente). Idempotente.</summary>
    public virtual async Task<CompraPendiente> registrarCompraPendiente(string cedula, string numeroCompra, decimal valorTotal)
    {
        await repoYoPago.guardarPendiente(cedula, numeroCompra, valorTotal);
        var compra = (await repoYoPago.buscarPendiente(numeroCompra))!;
        await unidad.Confirmar();
        return compra;
    }

    /// <summary>VII.16: el cliente se identifica con cédula y contraseña y ve sus compras SSO pendientes.</summary>
    public virtual async Task<IReadOnlyList<CompraPendiente>> consultarCompraPendiente(Credenciales credenciales, string? numeroCompra)
    {
        await Autenticar(credenciales.Cedula, credenciales.Clave);
        var compras = await repoYoPago.comprasDe(credenciales.Cedula);
        return numeroCompra is null ? compras : compras.Where(c => c.NumeroCompra == numeroCompra).ToList();
    }

    /// <summary>
    /// Pasos VII.17 (WSCompraPendiente → SrvYoPago pagar(numeroCompra, datosPago)), VII.18 (→ RepoYoPago
    /// buscarPendiente · registrarPagoConfirmado) y VII.19 (→ IntegraSSO reportarPago). El pago queda hecho en
    /// YoPago aunque SSO no responda: el reporte se reintenta en segundo plano.
    /// </summary>
    public virtual async Task<ResultadoPago> pagar(string numeroCompra, DatosPago datosPago)
    {
        await Autenticar(datosPago.Cedula, datosPago.Clave);
        var compra = await repoYoPago.buscarPendiente(numeroCompra);
        if (compra is null || compra.Cedula != datosPago.Cedula)
        {
            throw new CompraNoEncontradaException(numeroCompra);
        }
        if (compra.Estado != "PENDIENTE")
        {
            throw new CompraYaPagadaException(numeroCompra);
        }
        var referencia = $"YP-{Guid.NewGuid().ToString("N")[..12].ToUpperInvariant()}";
        await repoYoPago.registrarPagoConfirmado(numeroCompra, datosPago.Medio, referencia);
        await unidad.Confirmar();
        var (reporte, resultado) = await Reportar(compra.Cedula, numeroCompra, compra.ValorTotal);
        return new ResultadoPago(numeroCompra, "PAGADA", referencia, compra.ValorTotal, reporte, resultado);
    }

    /// <summary>Pagos que no se pudieron reportar a SSO: se reportan de nuevo (VII.19).</summary>
    public virtual async Task reintentarReportes()
    {
        foreach (var compra in await repoYoPago.reportesPendientes())
        {
            await Reportar(compra.Cedula, compra.NumeroCompra, compra.ValorTotal);
        }
    }

    private async Task<(string Reporte, string? Resultado)> Reportar(string cedula, string numeroCompra, decimal valorPagado)
    {
        try
        {
            var respuesta = await integraSSO.reportarPago(cedula, numeroCompra, valorPagado);
            await repoYoPago.registrarReporte(numeroCompra, respuesta.Resultado);
            await unidad.Confirmar();
            return ("REPORTADO", respuesta.Resultado);
        }
        catch (SsoNoDisponibleException e)
        {
            log.LogWarning("Reporte de {NumeroCompra} a SSO pendiente: {Mensaje}", numeroCompra, e.Message);
            await repoYoPago.registrarIntentoReporte(numeroCompra);
            await unidad.Confirmar();
            return ("PENDIENTE", null);
        }
    }

    private async Task Autenticar(string cedula, string clave)
    {
        var hash = await repoYoPago.claveDe(cedula);
        if (hash is null || !Claves.Verificar(clave, hash))
        {
            throw new CredencialesInvalidasException();
        }
    }
}

using Microsoft.AspNetCore.Mvc;
using MSPago.modelo;

namespace MSPago;

/// <summary>Servicio web de pagos. Lo invoca SrvRuteo (APIGateway, Java → .NET) por HTTP.</summary>
[ApiController]
[Route("pagos")]
public sealed class WSPagoYoPago(SrvPago srvPago) : ControllerBase
{
    /// <summary>Pasos VII.24 (SrvRuteo → WSPagoYoPago reportarPago(cedula, numeroCompra, valorPagado)) y VII.25 (→ SrvPago conciliarPago).</summary>
    [HttpPost("reporte")]
    public async Task<ResultadoConciliacion> reportarPago(ReportePago reporte) =>
        await srvPago.conciliarPago(reporte.Cedula, reporte.NumeroCompra, reporte.ValorPagado);
}

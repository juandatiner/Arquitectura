using Microsoft.AspNetCore.Mvc;
using YoPagoBackEnd.modelo;

namespace YoPagoBackEnd;

/// <summary>Servicio web de YoPago. Lo invocan IntegraYoPago (MSPago, HTTP/REST) y ProxyWSYoPagoWeb (YoPagoFrontEnd, HTTP).</summary>
[ApiController]
[Route("compras-pendientes")]
public sealed class WSCompraPendiente(SrvYoPago srvYoPago) : ControllerBase
{
    /// <summary>Pasos VII.7 (IntegraYoPago → WSCompraPendiente registrarCompraPendiente) y VII.8 (→ SrvYoPago).</summary>
    [HttpPost]
    public async Task<CompraPendiente> registrarCompraPendiente(SolicitudPendiente solicitud) =>
        await srvYoPago.registrarCompraPendiente(solicitud.Cedula, solicitud.NumeroCompra, solicitud.ValorTotal);

    /// <summary>Paso VII.16 (ProxyWSYoPagoWeb → WSCompraPendiente consultarCompraPendiente): las compras SSO pendientes del cliente.</summary>
    [HttpPost("consulta")]
    public async Task<IReadOnlyList<CompraPendiente>> consultarCompraPendiente(Consulta consulta) =>
        await srvYoPago.consultarCompraPendiente(new Credenciales(consulta.Cedula, consulta.Clave), consulta.NumeroCompra);

    /// <summary>Pasos VII.16 (ProxyWSYoPagoWeb → WSCompraPendiente pagar) y VII.17 (→ SrvYoPago pagar(numeroCompra, datosPago)).</summary>
    [HttpPost("{numeroCompra}/pagar")]
    public async Task<ResultadoPago> pagar(string numeroCompra, DatosPago datosPago) =>
        await srvYoPago.pagar(numeroCompra, datosPago);
}

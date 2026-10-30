using System.Net.Http.Json;
using YoPagoBackEnd.modelo;

namespace YoPagoBackEnd;

/// <summary>Integración con SSO: reporta el pago a Nginx (BALANCEADOR DE CARGAS, Máquina 1) por HTTPS.</summary>
public class IntegraSSO(HttpClient http, string credencial)
{
    /// <summary>
    /// Pasos VII.19 (SrvYoPago → IntegraSSO reportarPago(cedula, numeroCompra, valorPagado, credencial)) y VII.20
    /// (→ Nginx POST /api/pagos/reporte, HTTPS). La credencial identifica a YoPago ante el APIGateway.
    /// </summary>
    public virtual async Task<RespuestaSso> reportarPago(string cedula, string numeroCompra, decimal valorPagado)
    {
        try
        {
            using var respuesta = await http.PostAsJsonAsync(
                "api/pagos/reporte", new { cedula, numeroCompra, valorPagado, credencial }, Json.Opciones);
            if (!respuesta.IsSuccessStatusCode)
            {
                throw new SsoNoDisponibleException($"SSO respondió {(int)respuesta.StatusCode}");
            }
            return (await respuesta.Content.ReadFromJsonAsync<RespuestaSso>(Json.Opciones))!;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
        {
            throw new SsoNoDisponibleException("SSO no responde", e);
        }
    }
}

using System.Net.Http.Json;
using MSPago.modelo;

namespace MSPago;

/// <summary>Integración con YoPago (sistema externo, WSCompraPendiente · YoPago SERVER :5001) por HTTP/REST.</summary>
public class IntegraYoPago(HttpClient http)
{
    /// <summary>Pasos VII.6 (SrvPago → IntegraYoPago registrarPendiente(cedula, numeroCompra, valorTotal)) y VII.7 (→ WSCompraPendiente).</summary>
    public virtual async Task registrarPendiente(string cedula, string numeroCompra, decimal valorTotal)
    {
        try
        {
            using var respuesta = await http.PostAsJsonAsync("compras-pendientes", new { cedula, numeroCompra, valorTotal }, Json.Opciones);
            if (!respuesta.IsSuccessStatusCode)
            {
                throw new YoPagoNoDisponibleException($"YoPago respondió {(int)respuesta.StatusCode}");
            }
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
        {
            throw new YoPagoNoDisponibleException("YoPago no responde", e);
        }
    }
}

using System.Net.Http.Json;
using System.Text.Json;
using YoPagoFrontEnd.modelo;

namespace YoPagoFrontEnd;

/// <summary>Proxy HTTP hacia WSCompraPendiente (YoPago SERVER :5001). No toma decisiones: solo llama y devuelve.</summary>
public class ProxyWSYoPagoWeb(HttpClient http)
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    /// <summary>Pasos VII.15 (PantallaWebYoPago → ProxyWSYoPagoWeb consultarCompraPendiente) y VII.16 (→ WSCompraPendiente, HTTP).</summary>
    public virtual async Task<IReadOnlyList<CompraPendiente>> consultarCompraPendiente(string cedula, string clave, string? numeroCompra)
    {
        using var respuesta = await http.PostAsJsonAsync("compras-pendientes/consulta", new { cedula, clave, numeroCompra }, Json);
        await Verificar(respuesta);
        return (await respuesta.Content.ReadFromJsonAsync<List<CompraPendiente>>(Json))!;
    }

    /// <summary>Pasos VII.15 (PantallaWebYoPago → ProxyWSYoPagoWeb pagar(numeroCompra, datosPago)) y VII.16 (→ WSCompraPendiente, HTTP).</summary>
    public virtual async Task<ResultadoPago> pagar(string numeroCompra, DatosPago datosPago)
    {
        using var respuesta = await http.PostAsJsonAsync($"compras-pendientes/{Uri.EscapeDataString(numeroCompra)}/pagar", datosPago, Json);
        await Verificar(respuesta);
        return (await respuesta.Content.ReadFromJsonAsync<ResultadoPago>(Json))!;
    }

    private static async Task Verificar(HttpResponseMessage respuesta)
    {
        if (respuesta.IsSuccessStatusCode)
        {
            return;
        }
        string detalle;
        try
        {
            detalle = (await respuesta.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("detail").GetString() ?? "Error de YoPago";
        }
        catch (Exception e) when (e is JsonException or KeyNotFoundException or InvalidOperationException)
        {
            detalle = "YoPago no está disponible en este momento";
        }
        throw new RespuestaYoPagoException((int)respuesta.StatusCode, detalle);
    }
}

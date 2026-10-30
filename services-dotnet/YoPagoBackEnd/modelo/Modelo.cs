using System.Text.Json;

namespace YoPagoBackEnd.modelo;

public static class Json
{
    public static readonly JsonSerializerOptions Opciones = new(JsonSerializerDefaults.Web);
}

/// <summary>Cuerpo de registrarCompraPendiente(cedula, numeroCompra, valorTotal) (VII.7).</summary>
public sealed record SolicitudPendiente(string Cedula, string NumeroCompra, decimal ValorTotal);

/// <summary>Cédula y contraseña del cliente en YoPago.</summary>
public sealed record Credenciales(string Cedula, string Clave)
{
    public override string ToString() => $"Credenciales {{ Cedula = {Cedula}, Clave = **** }}";
}

/// <summary>Cuerpo de consultarCompraPendiente (VII.15/VII.16): credenciales y, opcional, una compra.</summary>
public sealed record Consulta(string Cedula, string Clave, string? NumeroCompra)
{
    public override string ToString() => $"Consulta {{ Cedula = {Cedula}, NumeroCompra = {NumeroCompra} }}";
}

/// <summary>Parámetro datosPago de pagar(numeroCompra, datosPago) (VII.15 – VII.17).</summary>
public sealed record DatosPago(string Cedula, string Clave, string Medio, string Titular)
{
    public override string ToString() => $"DatosPago {{ Cedula = {Cedula}, Medio = {Medio}, Titular = {Titular}, Clave = **** }}";
}

/// <summary>Una compra SSO pendiente (o ya pagada) en YoPago.</summary>
public sealed record CompraPendiente(
    string NumeroCompra, string Cedula, decimal ValorTotal, string Estado, string? Referencia, string? Reporte, string? ResultadoSso);

/// <summary>Resultado del pago en YoPago y de su reporte a SSO.</summary>
public sealed record ResultadoPago(string NumeroCompra, string Estado, string Referencia, decimal ValorPagado, string Reporte, string? ResultadoSso);

/// <summary>Respuesta de SSO (WSPagoYoPago) al reporte.</summary>
public sealed record RespuestaSso(string NumeroCompra, string Resultado, string? ReferenciaPago);

public sealed class CredencialesInvalidasException() : Exception("Cédula o contraseña incorrectas");

public sealed class CompraNoEncontradaException(string numeroCompra) : Exception($"No hay una compra pendiente {numeroCompra} para el cliente");

public sealed class CompraYaPagadaException(string numeroCompra) : Exception($"La compra {numeroCompra} ya fue pagada");

public sealed class SsoNoDisponibleException(string detalle, Exception? causa = null) : Exception(detalle, causa);

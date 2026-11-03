namespace YoPagoFrontEnd.modelo;

/// <summary>Compra SSO tal como la devuelve WSCompraPendiente (solo para mostrarla).</summary>
public sealed record CompraPendiente(
    string NumeroCompra, string Cedula, decimal ValorTotal, string Estado, string? Referencia, string? Reporte, string? ResultadoSso);

/// <summary>Parámetro datosPago de pagar(numeroCompra, datosPago).</summary>
public sealed record DatosPago(string Cedula, string Clave, string Medio, string Titular)
{
    public override string ToString() => $"DatosPago {{ Cedula = {Cedula}, Medio = {Medio}, Titular = {Titular}, Clave = **** }}";
}

/// <summary>Resultado del pago tal como lo devuelve WSCompraPendiente.</summary>
public sealed record ResultadoPago(string NumeroCompra, string Estado, string Referencia, decimal ValorPagado, string Reporte, string? ResultadoSso);

/// <summary>Lo que muestra la pantalla de compras pendientes.</summary>
public sealed record VistaPendientes(string Nombre, IReadOnlyList<CompraPendiente> Compras, string? Resaltada);

/// <summary>Error que devolvió YoPagoBackEnd (detalle del ProblemDetails) para mostrarlo tal cual.</summary>
public sealed class RespuestaYoPagoException(int estado, string detalle) : Exception(detalle)
{
    public int Estado { get; } = estado;
}

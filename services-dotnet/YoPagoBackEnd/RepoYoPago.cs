using Dapper;
using YoPagoBackEnd.config;
using YoPagoBackEnd.modelo;

namespace YoPagoBackEnd;

/// <summary>Repositorio de DB YOPAGO (PostgreSQL).</summary>
public class RepoYoPago(UnidadDeTrabajo unidad)
{
    private const string Columnas =
        "numero_compra AS NumeroCompra, cedula AS Cedula, valor_total AS ValorTotal, estado AS Estado, referencia AS Referencia, reporte AS Reporte, resultado_sso AS ResultadoSso";

    /// <summary>Paso VII.9: guardarPendiente(cedula, numeroCompra, valorTotal). Idempotente por número de compra.</summary>
    public virtual async Task guardarPendiente(string cedula, string numeroCompra, decimal valorTotal) =>
        await (await unidad.Conexion()).ExecuteAsync(
            """
            INSERT INTO compra_pendiente (numero_compra, cedula, valor_total, estado)
            VALUES (@numeroCompra, @cedula, @valorTotal, 'PENDIENTE')
            ON CONFLICT (numero_compra) DO NOTHING
            """, new { cedula, numeroCompra, valorTotal }, unidad.Transaccion);

    /// <summary>Paso VII.18: buscarPendiente (bloquea la fila mientras se paga).</summary>
    public virtual async Task<CompraPendiente?> buscarPendiente(string numeroCompra) =>
        await (await unidad.Conexion()).QuerySingleOrDefaultAsync<CompraPendiente>(
            $"SELECT {Columnas} FROM compra_pendiente WHERE numero_compra = @numeroCompra FOR UPDATE",
            new { numeroCompra }, unidad.Transaccion);

    /// <summary>Paso VII.18: registrarPagoConfirmado. El reporte a SSO queda PENDIENTE hasta lograrlo.</summary>
    public virtual async Task registrarPagoConfirmado(string numeroCompra, string medioPago, string referencia) =>
        await (await unidad.Conexion()).ExecuteAsync(
            """
            UPDATE compra_pendiente SET estado = 'PAGADA', medio_pago = @medioPago, referencia = @referencia,
                   pagada_en = now(), reporte = 'PENDIENTE'
             WHERE numero_compra = @numeroCompra AND estado = 'PENDIENTE'
            """, new { numeroCompra, medioPago, referencia }, unidad.Transaccion);

    public virtual async Task<IReadOnlyList<CompraPendiente>> comprasDe(string cedula) =>
        (await (await unidad.Conexion()).QueryAsync<CompraPendiente>(
            $"SELECT {Columnas} FROM compra_pendiente WHERE cedula = @cedula ORDER BY creada_en DESC",
            new { cedula }, unidad.Transaccion)).ToList();

    public virtual async Task<string?> claveDe(string cedula) =>
        await (await unidad.Conexion()).ExecuteScalarAsync<string?>(
            "SELECT clave_hash FROM usuario WHERE cedula = @cedula", new { cedula }, unidad.Transaccion);

    public virtual async Task<IReadOnlyList<CompraPendiente>> reportesPendientes() =>
        (await (await unidad.Conexion()).QueryAsync<CompraPendiente>(
            $"SELECT {Columnas} FROM compra_pendiente WHERE reporte = 'PENDIENTE' ORDER BY pagada_en LIMIT 20 FOR UPDATE SKIP LOCKED",
            transaction: unidad.Transaccion)).ToList();

    public virtual async Task registrarReporte(string numeroCompra, string resultadoSso) =>
        await (await unidad.Conexion()).ExecuteAsync(
            "UPDATE compra_pendiente SET reporte = 'REPORTADO', resultado_sso = @resultadoSso WHERE numero_compra = @numeroCompra",
            new { numeroCompra, resultadoSso }, unidad.Transaccion);

    public virtual async Task registrarIntentoReporte(string numeroCompra) =>
        await (await unidad.Conexion()).ExecuteAsync(
            "UPDATE compra_pendiente SET intentos_reporte = intentos_reporte + 1 WHERE numero_compra = @numeroCompra",
            new { numeroCompra }, unidad.Transaccion);
}

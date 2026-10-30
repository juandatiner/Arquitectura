using Dapper;
using MSPago.config;
using MSPago.modelo;

namespace MSPago;

/// <summary>Repositorio de DB PAGO (PostgreSQL).</summary>
public class RepoPago(UnidadDeTrabajo unidad)
{
    /// <summary>Paso VII.5.</summary>
    public virtual async Task<bool> existe(string numeroCompra) =>
        await (await unidad.Conexion()).ExecuteScalarAsync<bool>(
            "SELECT EXISTS (SELECT 1 FROM pago WHERE numero_compra = @numeroCompra)", new { numeroCompra }, unidad.Transaccion);

    /// <summary>Paso VII.10: guardar(Pago).</summary>
    public virtual async Task guardar(Pago pago) =>
        await (await unidad.Conexion()).ExecuteAsync(
            """
            INSERT INTO pago (numero_compra, cedula, valor_ofertado, impuestos, valor_total, estado)
            VALUES (@NumeroCompra, @Cedula, @ValorOfertado, @Impuestos, @ValorTotal, @Estado)
            ON CONFLICT (numero_compra) DO NOTHING
            """, pago, unidad.Transaccion);

    /// <summary>Paso VII.26: obtenerPorCompra (bloquea la fila mientras se concilia).</summary>
    public virtual async Task<Pago?> obtenerPorCompra(string numeroCompra) =>
        await (await unidad.Conexion()).QuerySingleOrDefaultAsync<Pago>(
            """
            SELECT numero_compra AS NumeroCompra, cedula AS Cedula, valor_ofertado AS ValorOfertado,
                   impuestos AS Impuestos, valor_total AS ValorTotal, estado AS Estado
              FROM pago WHERE numero_compra = @numeroCompra FOR UPDATE
            """, new { numeroCompra }, unidad.Transaccion);

    /// <summary>Paso VII.26: confirmarSiPendiente. UPDATE condicional: un solo resultado por pago.</summary>
    public virtual async Task<bool> confirmarSiPendiente(string numeroCompra, string estado, decimal valorPagado, string? referenciaPago) =>
        await (await unidad.Conexion()).ExecuteAsync(
            """
            UPDATE pago SET estado = @estado, valor_pagado = @valorPagado, referencia_pago = @referenciaPago, conciliado_en = now()
             WHERE numero_compra = @numeroCompra AND estado = 'PENDIENTE'
            """, new { numeroCompra, estado, valorPagado, referenciaPago }, unidad.Transaccion) == 1;
}

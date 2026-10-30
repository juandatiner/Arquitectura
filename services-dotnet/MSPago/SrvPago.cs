using System.Globalization;
using Microsoft.Extensions.Options;
using MSPago.config;
using MSPago.modelo;

namespace MSPago;

/// <summary>Prepara el pago en YoPago y concilia el pago que YoPago reporta.</summary>
public class SrvPago(
    RepoPago repoPago,
    RepoOutboxPago repoOutboxPago,
    IntegraYoPago integraYoPago,
    UnidadDeTrabajo unidad,
    IOptions<OpcionesPago> opciones)
{
    private static readonly CultureInfo Colombia = CultureInfo.GetCultureInfo("es-CO");

    /// <summary>
    /// Pasos VII.4 (ListenerPagoSolicitud → SrvPago prepararPago(numeroCompra, cedula, valorOfertado)),
    /// VII.5 (→ RepoPago existe), VII.6 (→ IntegraYoPago registrarPendiente(cedula, numeroCompra, valorTotal)),
    /// VII.10 (→ RepoPago guardar(Pago)) y VII.11 (→ RepoOutboxPago guardar(NotificacionPago)).
    /// <paramref name="contacto"/>: a quién avisar el valor y la URL de pago (observación 15).
    /// </summary>
    public virtual async Task prepararPago(string numeroCompra, string cedula, decimal valorOfertado, Destinatario? contacto)
    {
        if (await repoPago.existe(numeroCompra))
        {
            return;
        }
        var impuestos = decimal.Round(valorOfertado * opciones.Value.TasaImpuesto, 0, MidpointRounding.AwayFromZero);
        var valorTotal = valorOfertado + impuestos;

        // YoPago es idempotente por número de compra: si esto se reintenta, no duplica la compra pendiente.
        await integraYoPago.registrarPendiente(cedula, numeroCompra, valorTotal);

        await repoPago.guardar(new Pago(numeroCompra, cedula, valorOfertado, impuestos, valorTotal, "PENDIENTE"));
        if (contacto is not null)
        {
            var url = $"{opciones.Value.UrlPago.TrimEnd('/')}/?compra={Uri.EscapeDataString(numeroCompra)}";
            await repoOutboxPago.guardar(new NotificacionPago(
                Guid.NewGuid(),
                "NotificacionPago",
                numeroCompra,
                contacto,
                $"Puede continuar su compra {numeroCompra}",
                $"Su compra {numeroCompra} fue aprobada. Valor a pagar: {Pesos(valorTotal)} (oferta {Pesos(valorOfertado)} + impuestos {Pesos(impuestos)}). Pague en YoPago: {url}",
                true));
        }
        await unidad.Confirmar();
    }

    /// <summary>
    /// Pasos VII.25 (WSPagoYoPago → SrvPago conciliarPago(cedula, numeroCompra, valorPagado)), VII.26 (→ RepoPago
    /// obtenerPorCompra · confirmarSiPendiente) y VII.27 (→ RepoOutboxPago guardar(PagoConfirmado)). El pago se
    /// aprueba solo si la cédula y el valor coinciden con lo pendiente; un reporte repetido no genera otro evento.
    /// </summary>
    public virtual async Task<ResultadoConciliacion> conciliarPago(string cedula, string numeroCompra, decimal valorPagado)
    {
        var pago = await repoPago.obtenerPorCompra(numeroCompra) ?? throw new PagoNoEncontradoException(numeroCompra);
        if (pago.Estado != "PENDIENTE")
        {
            return new ResultadoConciliacion(numeroCompra, pago.Estado == "PAGADO" ? "APROBADO" : "RECHAZADO", null);
        }
        var aprobado = pago.Cedula == cedula && pago.ValorTotal == valorPagado;
        var resultado = aprobado ? "APROBADO" : "RECHAZADO";
        var referencia = aprobado ? $"PAGO-{Guid.NewGuid().ToString("N")[..12].ToUpperInvariant()}" : null;
        if (await repoPago.confirmarSiPendiente(numeroCompra, aprobado ? "PAGADO" : "RECHAZADO", valorPagado, referencia))
        {
            await repoOutboxPago.guardar(new PagoConfirmado(Guid.NewGuid(), numeroCompra, cedula, valorPagado, referencia, resultado));
        }
        await unidad.Confirmar();
        return new ResultadoConciliacion(numeroCompra, resultado, referencia);
    }

    private static string Pesos(decimal valor) => valor.ToString("C0", Colombia);
}

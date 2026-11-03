using System.Text.Json;
using System.Text.Json.Serialization;

namespace MSPago.modelo;

/// <summary>JSON en camelCase y con los campos en el orden del contrato (docs/modelo/eventos.md).</summary>
public static class Json
{
    public static readonly JsonSerializerOptions Opciones = new(JsonSerializerDefaults.Web);
}

/// <summary>A quién se le envía una notificación.</summary>
public sealed record Destinatario(string? Nombre, string? Correo, string? Celular);

/// <summary>Mensaje de q.pagoSolicitud (VII.2 Java → .NET, VII.3).</summary>
public sealed record PagoSolicitud(
    Guid IdEvento, string NumeroCompra, string Cedula, decimal ValorOfertado, string? Descripcion, Destinatario? Destinatario);

/// <summary>Parámetro de guardar(Pago) (VII.10).</summary>
public sealed record Pago(string NumeroCompra, string Cedula, decimal ValorOfertado, decimal Impuestos, decimal ValorTotal, string Estado);

/// <summary>Cuerpo de reportarPago(cedula, numeroCompra, valorPagado) (VII.24).</summary>
public sealed record ReportePago(string Cedula, string NumeroCompra, decimal ValorPagado);

/// <summary>Resultado de conciliarPago: APROBADO o RECHAZADO.</summary>
public sealed record ResultadoConciliacion(string NumeroCompra, string Resultado, string? ReferenciaPago);

/// <summary>Evento guardado en el outbox; <see cref="Cola"/> es la cola q.* del diagrama.</summary>
public interface IEvento
{
    Guid IdEvento { get; }

    [JsonIgnore]
    string Cola { get; }

    [JsonIgnore]
    string Tipo { get; }
}

/// <summary>VII.11 / VII.13 → q.notificacion: valor a pagar (oferta + impuestos) y URL de pago, por SMS.</summary>
public sealed record NotificacionPago(
    Guid IdEvento, string Origen, string NumeroCompra, Destinatario Destinatario, string Asunto, string Cuerpo, bool Sms) : IEvento
{
    [JsonIgnore] public string Cola => "q.notificacion";
    [JsonIgnore] public string Tipo => "NotificacionPago";
}

/// <summary>VII.27 / VII.29 → q.pagoConfirmado (.NET → Java). Resultado: APROBADO o RECHAZADO.</summary>
public sealed record PagoConfirmado(
    Guid IdEvento, string NumeroCompra, string Cedula, decimal ValorPagado, string? ReferenciaPago, string Resultado) : IEvento
{
    [JsonIgnore] public string Cola => "q.pagoConfirmado";
    [JsonIgnore] public string Tipo => "PagoConfirmado";
}

/// <summary>Fila PENDIENTE del outbox lista para publicarse.</summary>
public sealed record EventoPendiente(Guid IdEvento, string Tipo, string Cola, string Payload);

/// <summary>No existe un pago pendiente para esa compra.</summary>
public sealed class PagoNoEncontradoException(string numeroCompra) : Exception($"No existe un pago para la compra {numeroCompra}");

/// <summary>YoPago no respondió: la solicitud se reintenta desde la cola.</summary>
public sealed class YoPagoNoDisponibleException(string detalle, Exception? causa = null) : Exception(detalle, causa);

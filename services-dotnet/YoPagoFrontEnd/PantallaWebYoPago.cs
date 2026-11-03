using Microsoft.AspNetCore.Mvc;
using YoPagoFrontEnd.modelo;

namespace YoPagoFrontEnd;

/// <summary>Pantalla del sitio transaccional YoPago (controlador MVC con el nombre exacto del diagrama). Solo presentación.</summary>
[Controller]
[Route("")]
public sealed class PantallaWebYoPago(ProxyWSYoPagoWeb proxyWSYoPagoWeb) : Controller
{
    private const string Cedula = "cedula";
    private const string Clave = "clave";

    /// <summary>Formulario de ingreso (cédula y contraseña). <paramref name="compra"/> llega en la URL de pago del SMS.</summary>
    [HttpGet("")]
    public IActionResult Ingresar(string? compra) => View("Ingresar", compra);

    /// <summary>Paso VII.14 (Browser → PantallaWebYoPago consultarCompraPendiente(numeroCompra)) y VII.15 (→ ProxyWSYoPagoWeb).</summary>
    [HttpPost("consultar")]
    public async Task<IActionResult> consultarCompraPendiente(string cedula, string clave, string? compra)
    {
        try
        {
            var compras = await proxyWSYoPagoWeb.consultarCompraPendiente(cedula, clave, null);
            HttpContext.Session.SetString(Cedula, cedula);
            HttpContext.Session.SetString(Clave, clave);
            return View("Pendientes", new VistaPendientes(cedula, compras, compra));
        }
        catch (RespuestaYoPagoException e)
        {
            ViewData["Error"] = e.Message;
            return View("Ingresar", compra);
        }
    }

    /// <summary>Paso VII.14 (Browser → PantallaWebYoPago confirmarPago(...)) y VII.15 (→ ProxyWSYoPagoWeb pagar(numeroCompra, datosPago)).</summary>
    [HttpPost("pagar")]
    public async Task<IActionResult> confirmarPago(string numeroCompra, string medio, string titular)
    {
        var cedula = HttpContext.Session.GetString(Cedula);
        var clave = HttpContext.Session.GetString(Clave);
        if (cedula is null || clave is null)
        {
            ViewData["Error"] = "La sesión expiró, ingrese de nuevo";
            return View("Ingresar", numeroCompra);
        }
        try
        {
            return View("Resultado", await proxyWSYoPagoWeb.pagar(numeroCompra, new DatosPago(cedula, clave, medio, titular)));
        }
        catch (RespuestaYoPagoException e)
        {
            ViewData["Error"] = e.Message;
            return View("Pendientes", new VistaPendientes(cedula, await proxyWSYoPagoWeb.consultarCompraPendiente(cedula, clave, null), numeroCompra));
        }
    }

    [HttpPost("salir")]
    public IActionResult Salir()
    {
        HttpContext.Session.Clear();
        return RedirectToAction(nameof(Ingresar));
    }
}

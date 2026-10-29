package co.edu.javeriana.sso.apigateway.modelo;

import java.math.BigDecimal;

/**
 * Parámetro {@code solicitud} de SrvSeguridadToken.validarYAutorizar(solicitud): una variante por
 * caso de uso. El token viaja en la cabecera {@code Authorization: Bearer}.
 */
public sealed interface Solicitud {

    /** II.5 · consultarCatalogo(filtros, token). */
    record ConsultaCatalogo(Filtros filtros, String token) implements Solicitud {}

    /** II.5 · suscribirse(criterio, token). */
    record Suscripcion(CriterioSuscripcion criterio, String token) implements Solicitud {}

    /** II.5 · cancelarSuscripcion(idSuscripcion, token) (camino b de II.7b). */
    record CancelacionSuscripcion(String idSuscripcion, String token) implements Solicitud {}

    /** III.5 · publicarNuevaSubasta(idSubasta, token): solo ADMIN. */
    record Publicacion(String idSubasta, String token) implements Solicitud {}

    /** IV.5 · registrarOferta(idSubasta, valor, token). */
    record Oferta(String idSubasta, BigDecimal valor, String token) implements Solicitud {}

    /** VI.5 · iniciarCompra(numeroCompra, token). */
    record InicioCompra(String numeroCompra, String token) implements Solicitud {}

    /** VI.1 · consultarMisCompras(token). Ver observación 6 de docs/modelo/observaciones.md. */
    record ConsultaCompras(String token) implements Solicitud {}

    /** VII.22 · reportarPago(cedula, numeroCompra, valorPagado, credencial): sistema YoPago. */
    record ReportePago(
            String cedula, String numeroCompra, BigDecimal valorPagado, String credencial)
            implements Solicitud {}
}

package co.edu.javeriana.sso.mssubasta.modelo;

import java.math.BigDecimal;

/**
 * Respuesta de registrarOferta. El cliente no espera el resultado de la subasta: se le informa que
 * le llegará un mensaje al celular cuando sea posible continuar la compra (enunciado).
 */
public record OfertaRegistrada(
        long idOferta, String idSubasta, BigDecimal valor, EstadoOferta estado, String mensaje) {

    public static final String MENSAJE =
            "Oferta registrada. Le llegará un mensaje al celular cuando sea posible continuar la compra.";
}

package co.edu.javeriana.sso.apigateway.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Cuerpo de reportarPago(cedula, numeroCompra, valorPagado, credencial) (paso VII.21). */
public record ReportePago(
        @NotBlank String cedula,
        @NotBlank String numeroCompra,
        @NotNull @Positive BigDecimal valorPagado,
        @NotBlank String credencial) {

    @Override
    public String toString() {
        return "ReportePago[cedula="
                + cedula
                + ", numeroCompra="
                + numeroCompra
                + ", valorPagado="
                + valorPagado
                + ", credencial=****]";
    }
}

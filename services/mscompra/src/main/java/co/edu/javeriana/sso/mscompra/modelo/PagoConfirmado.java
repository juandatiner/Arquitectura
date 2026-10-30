package co.edu.javeriana.sso.mscompra.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Mensaje de q.pagoConfirmado (VII.29 .NET → Java, VII.30). */
public record PagoConfirmado(
        UUID idEvento,
        String numeroCompra,
        String cedula,
        BigDecimal valorPagado,
        String referenciaPago,
        ResultadoPago resultado) {}

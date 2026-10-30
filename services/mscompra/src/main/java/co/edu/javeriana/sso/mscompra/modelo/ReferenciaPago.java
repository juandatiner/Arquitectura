package co.edu.javeriana.sso.mscompra.modelo;

import java.math.BigDecimal;

/**
 * Parámetro {@code referenciaPago} de procesarPago(numeroCompra, referenciaPago) (VII.31): la
 * referencia del pago en YoPago y si la conciliación de MSPago lo aprobó o lo rechazó (VII.32a /
 * VII.32b).
 */
public record ReferenciaPago(String referencia, ResultadoPago resultado, BigDecimal valorPagado) {}

package co.edu.javeriana.sso.apigateway.modelo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Cuerpo de registrarOferta: el idSubasta va en la ruta /api/subastas/{id}/ofertas (paso IV.3). */
public record Oferta(@NotNull @Positive BigDecimal valor) {}

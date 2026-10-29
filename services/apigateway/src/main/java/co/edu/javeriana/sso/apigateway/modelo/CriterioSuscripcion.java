package co.edu.javeriana.sso.apigateway.modelo;

import jakarta.validation.constraints.NotBlank;

/**
 * Criterio de suscribirse(criterio) (paso II.1): a nuevas subastas de una categoría o a los cambios
 * de oferta de una subasta.
 */
public record CriterioSuscripcion(@NotBlank String tipo, @NotBlank String valor) {}

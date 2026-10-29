package co.edu.javeriana.sso.apigateway.modelo;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de publicarNuevaSubasta(idSubasta, token) (paso III.3). */
public record Publicacion(@NotBlank String idSubasta) {}

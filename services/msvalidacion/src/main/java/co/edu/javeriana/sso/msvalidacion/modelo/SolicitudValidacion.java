package co.edu.javeriana.sso.msvalidacion.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de validar(numeroCompra, cedula) (paso VI.14). */
public record SolicitudValidacion(
        @NotBlank String numeroCompra, @Pattern(regexp = "\\d{5,15}") String cedula) {}

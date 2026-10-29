package co.edu.javeriana.sso.mssubasta.modelo;

import jakarta.validation.constraints.NotBlank;

/**
 * Parámetro {@code cliente} de registrarOferta(idSubasta, cliente, valor), tomado del token por el
 * gateway.
 */
public record Cliente(
        @NotBlank String usuario,
        @NotBlank String cedula,
        String nombre,
        String celular,
        String correo,
        String rol) {}

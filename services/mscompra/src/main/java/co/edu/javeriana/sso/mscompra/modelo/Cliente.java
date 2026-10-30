package co.edu.javeriana.sso.mscompra.modelo;

import jakarta.validation.constraints.NotBlank;

/**
 * Identidad del cliente tomada del token por el gateway (incluye la dirección registrada en DB
 * ACCESO).
 */
public record Cliente(
        @NotBlank String usuario,
        @NotBlank String cedula,
        String nombre,
        String celular,
        String correo,
        String direccion,
        String rol) {}

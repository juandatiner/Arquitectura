package co.edu.javeriana.sso.mssuscripcion.modelo;

import jakarta.validation.constraints.NotBlank;

/** Parámetro {@code cliente}: identidad tomada del token por el gateway. */
public record Cliente(
        @NotBlank String usuario,
        @NotBlank String cedula,
        String nombre,
        String celular,
        String correo,
        String rol) {}

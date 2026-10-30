package co.edu.javeriana.sso.msvalidacion.modelo;

/** Respuesta de validar(numeroCompra, cedula). */
public record ResultadoValidacion(String numeroCompra, EstadoOFAC estado) {}

package co.edu.javeriana.sso.mscompra.modelo;

/** Respuesta de WSValidacion.validar(numeroCompra, cedula). */
public record ResultadoValidacion(String numeroCompra, EstadoOFAC estado) {}

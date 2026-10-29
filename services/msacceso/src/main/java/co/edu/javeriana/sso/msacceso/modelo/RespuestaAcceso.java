package co.edu.javeriana.sso.msacceso.modelo;

import java.time.Instant;

/** Respuesta de autenticar: token firmado para las siguientes peticiones y datos del cliente. */
public record RespuestaAcceso(String token, Instant expira, DatosCliente cliente) {}

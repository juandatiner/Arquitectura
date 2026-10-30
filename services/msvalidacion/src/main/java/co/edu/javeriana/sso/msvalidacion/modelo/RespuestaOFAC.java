package co.edu.javeriana.sso.msvalidacion.modelo;

/** Respuesta de WSOfac.validarCedula(cedula) (paso VI.18). */
public record RespuestaOFAC(String cedula, EstadoOFAC estado) {}

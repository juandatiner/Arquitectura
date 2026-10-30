package co.edu.javeriana.sso.msnotificacion.modelo;

/** A quién se le envía la notificación. */
public record Destinatario(String nombre, String correo, String celular) {}

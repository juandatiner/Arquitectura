package co.edu.javeriana.sso.mssuscripcion.modelo;

/** A quién se le envía una notificación (datos del suscriptor). */
public record Destinatario(String nombre, String correo, String celular) {}

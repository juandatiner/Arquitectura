package co.edu.javeriana.sso.apigateway.modelo;

/**
 * Parámetro de evaluarAcceso(rutaLogin) (paso I.5): la ruta pública de login y sus credenciales.
 */
public record RutaLogin(String ruta, Credenciales credenciales) {}

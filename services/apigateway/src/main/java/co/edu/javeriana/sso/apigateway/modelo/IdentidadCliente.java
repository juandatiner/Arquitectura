package co.edu.javeriana.sso.apigateway.modelo;

/**
 * Cliente autenticado (la dirección es la registrada en DB ACCESO, para el envío del CU VIII),
 * extraído del token. Es el parámetro {@code cliente} / {@code admin} que SrvRuteo envía a los
 * componentes internos (pasos III.6, IV.6, VI.6).
 */
public record IdentidadCliente(
        String usuario,
        String cedula,
        String nombre,
        String celular,
        String correo,
        String direccion,
        String rol) {

    public boolean esAdmin() {
        return "ADMIN".equals(rol);
    }
}

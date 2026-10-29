package co.edu.javeriana.sso.mscatalogo.modelo;

/**
 * Parámetro {@code admin} de publicarNuevaSubasta(idSubasta, admin), tomado del token por el
 * gateway.
 */
public record Administrador(
        String usuario, String cedula, String nombre, String celular, String correo, String rol) {}

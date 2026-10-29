package co.edu.javeriana.sso.msacceso.modelo;

/** Datos del cliente en DB ACCESO, devueltos por RepoLogin.obtenerDatosCliente (paso I.11). */
public record DatosCliente(
        String usuario,
        String cedula,
        String nombre,
        String celular,
        String correo,
        String direccion,
        Rol rol) {}

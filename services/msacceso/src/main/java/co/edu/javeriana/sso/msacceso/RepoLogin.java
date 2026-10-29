package co.edu.javeriana.sso.msacceso;

import co.edu.javeriana.sso.msacceso.modelo.ClienteNoRegistradoException;
import co.edu.javeriana.sso.msacceso.modelo.DatosCliente;
import co.edu.javeriana.sso.msacceso.modelo.Rol;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB ACCESO (PostgreSQL, JDBC). */
@Repository
public class RepoLogin {

    private final JdbcClient jdbc;

    public RepoLogin(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso I.11 (SrvLogin → RepoLogin). */
    public DatosCliente obtenerDatosCliente(String identidadValidada) {
        return jdbc.sql(
                        """
                        SELECT usuario, cedula, nombre, celular, correo, direccion, rol
                          FROM cliente
                         WHERE usuario = :usuario
                        """)
                .param("usuario", identidadValidada)
                .query(
                        (rs, n) ->
                                new DatosCliente(
                                        rs.getString("usuario"),
                                        rs.getString("cedula"),
                                        rs.getString("nombre"),
                                        rs.getString("celular"),
                                        rs.getString("correo"),
                                        rs.getString("direccion"),
                                        Rol.valueOf(rs.getString("rol"))))
                .optional()
                .orElseThrow(ClienteNoRegistradoException::new);
    }
}

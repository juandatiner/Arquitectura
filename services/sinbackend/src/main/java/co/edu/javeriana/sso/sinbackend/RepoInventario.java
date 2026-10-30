package co.edu.javeriana.sso.sinbackend;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB SIN (PostgreSQL, JDBC). */
@Repository
public class RepoInventario {

    private final JdbcClient jdbc;

    public RepoInventario(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso VIII.11a. */
    public boolean existePorCompra(String numeroCompra) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM salida_inventario WHERE numero_compra = :numero)")
                .param("numero", numeroCompra)
                .query(Boolean.class)
                .single();
    }

    /** Paso VIII.11a: almacena la salida del bien vendido. */
    public void registrarSalida(String numeroCompra, String idBien) {
        jdbc.sql("INSERT INTO salida_inventario (numero_compra, id_bien) VALUES (:numero, :bien)")
                .param("numero", numeroCompra)
                .param("bien", idBien)
                .update();
    }
}

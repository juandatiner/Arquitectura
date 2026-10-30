package co.edu.javeriana.sso.senbackend;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB SEN (PostgreSQL, JDBC). */
@Repository
public class RepoEnvio {

    private final JdbcClient jdbc;

    public RepoEnvio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso VIII.11b. */
    public boolean existePorCompra(String numeroCompra) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM envio WHERE numero_compra = :numero)")
                .param("numero", numeroCompra)
                .query(Boolean.class)
                .single();
    }

    /** Paso VIII.11b: almacena el envío programado. */
    public void programar(
            String numeroCompra, String idBien, String direccion, LocalDate fechaProgramada) {
        jdbc.sql(
                        """
                        INSERT INTO envio (numero_compra, id_bien, direccion, fecha_programada, estado)
                        VALUES (:numero, :bien, :direccion, :fecha, 'PROGRAMADO')
                        """)
                .param("numero", numeroCompra)
                .param("bien", idBien)
                .param("direccion", direccion)
                .param("fecha", fechaProgramada)
                .update();
    }
}

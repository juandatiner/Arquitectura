package co.edu.javeriana.sso.msdespacho;

import co.edu.javeriana.sso.msdespacho.modelo.Despacho;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Repositorio de DB DESPACHO (PostgreSQL, JDBC). */
@Repository
public class RepoDespacho {

    private final JdbcClient jdbc;

    public RepoDespacho(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Paso VIII.5. */
    public boolean existePorCompra(String numeroCompra) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM despacho WHERE numero_compra = :numero)")
                .param("numero", numeroCompra)
                .query(Boolean.class)
                .single();
    }

    /** Paso VIII.5: crear(Despacho) en estado SOLICITADO. */
    public void crear(Despacho despacho) {
        jdbc.sql(
                        """
                        INSERT INTO despacho (numero_compra, id_bien, direccion, estado)
                        VALUES (:numero, :bien, :direccion, 'SOLICITADO')
                        """)
                .param("numero", despacho.numeroCompra())
                .param("bien", despacho.idBien())
                .param("direccion", despacho.direccion())
                .update();
    }

    /** Paso VIII.17. Idempotente. */
    public void marcarInventarioConfirmado(String numeroCompra) {
        jdbc.sql("UPDATE despacho SET inventario_confirmado = true WHERE numero_compra = :numero")
                .param("numero", numeroCompra)
                .update();
    }

    /** Paso VIII.17. Idempotente. */
    public void marcarEnvioConfirmado(String numeroCompra) {
        jdbc.sql("UPDATE despacho SET envio_confirmado = true WHERE numero_compra = :numero")
                .param("numero", numeroCompra)
                .update();
    }

    /**
     * Paso VIII.18: pasa a CONFIRMADO solo si SIN y SEN confirmaron y aún estaba SOLICITADO.
     *
     * @return true una única vez por despacho
     */
    public boolean confirmarSiAmbos(String numeroCompra) {
        return jdbc.sql(
                                """
                                UPDATE despacho SET estado = 'CONFIRMADO', confirmado_en = now()
                                 WHERE numero_compra = :numero AND estado = 'SOLICITADO'
                                   AND inventario_confirmado AND envio_confirmado
                                """)
                        .param("numero", numeroCompra)
                        .update()
                == 1;
    }
}

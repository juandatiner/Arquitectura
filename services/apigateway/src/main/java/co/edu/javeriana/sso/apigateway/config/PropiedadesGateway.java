package co.edu.javeriana.sso.apigateway.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del APIGateway.
 *
 * @param rutas URL base de cada destino de SrvRuteo (flechas HTTP del diagrama)
 * @param token validación del token emitido por MSAcceso
 * @param credencialYoPago credencial que YoPago envía en reportarPago (paso VII.21)
 * @param timeoutConexion tiempo máximo para conectar con un componente interno
 * @param timeoutRespuesta tiempo máximo de espera de la respuesta
 */
@ConfigurationProperties("sso")
public record PropiedadesGateway(
        Rutas rutas,
        Token token,
        String credencialYoPago,
        Duration timeoutConexion,
        Duration timeoutRespuesta) {

    /**
     * @param msAcceso WSAcceso · MSAcceso SERVER :8081
     * @param msCatalogo WSCatalogo · MSCatalogo SERVER :8082
     * @param msSubasta WSSubasta · MSSubasta SERVER :8083
     * @param msSuscripcion WSSuscripcion · MSSuscripcion SERVER :8084
     * @param balanceadorCompra Nginx upstream msCompra (least_conn) → WSCompra réplicas 1 y 2
     * @param msPago WSPagoYoPago · MSPago SERVER :5086 (.NET)
     */
    public record Rutas(
            String msAcceso,
            String msCatalogo,
            String msSubasta,
            String msSuscripcion,
            String balanceadorCompra,
            String msPago) {}

    /**
     * @param emisor valor esperado del claim iss
     * @param archivoClavePublica PEM con la clave pública RSA (par de la privada de MSAcceso)
     */
    public record Token(String emisor, Path archivoClavePublica) {}
}

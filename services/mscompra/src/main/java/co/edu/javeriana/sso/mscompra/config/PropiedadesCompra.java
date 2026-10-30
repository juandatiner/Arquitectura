package co.edu.javeriana.sso.mscompra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param urlValidacion WSValidacion · MSValidacion SERVER :8087 (flecha HTTP/REST de VI.14)
 * @param tamanoLote compras por ciclo de TimerValidacionOFAC (tamañoLote de VI.12)
 * @param reservaValidacion cuánto tiempo una réplica se reserva una compra mientras la valida
 * @param maximoIntentosOfac intentos sin respuesta antes de pasar a EN_REVISION (VI.22c)
 * @param esperaInicialOfac espera tras el primer intento sin respuesta (luego se duplica)
 * @param esperaMaximaOfac tope de la espera entre intentos
 * @param plazoDespacho tiempo máximo para que SIN y SEN confirmen antes de "despacho agotado" (X.9)
 * @param timeoutConexion tiempo máximo para conectar con MSValidacion
 * @param timeoutRespuesta tiempo máximo de respuesta de MSValidacion
 */
@ConfigurationProperties("sso.compra")
public record PropiedadesCompra(
        String urlValidacion,
        int tamanoLote,
        Duration reservaValidacion,
        int maximoIntentosOfac,
        Duration esperaInicialOfac,
        Duration esperaMaximaOfac,
        Duration plazoDespacho,
        Duration timeoutConexion,
        Duration timeoutRespuesta) {}

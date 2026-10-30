package co.edu.javeriana.sso.mssuscripcion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param tamanoLote eventos por ciclo de WorkerOutboxSuscripcion (parámetro tamañoLote de III.11)
 * @param esperaConfirmacion tiempo máximo para la confirmación de RabbitMQ
 */
@ConfigurationProperties("sso.outbox")
public record PropiedadesOutbox(int tamanoLote, Duration esperaConfirmacion) {}

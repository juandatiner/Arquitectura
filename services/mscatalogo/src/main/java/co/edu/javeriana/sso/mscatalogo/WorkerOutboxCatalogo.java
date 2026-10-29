package co.edu.javeriana.sso.mscatalogo;

import co.edu.javeriana.sso.mscatalogo.config.PropiedadesOutbox;
import co.edu.javeriana.sso.mscatalogo.modelo.EventoPendiente;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica en RabbitMQ los eventos guardados en el outbox. Si MOM SERVER no está disponible, los
 * eventos quedan PENDIENTES y se publican cuando vuelva: no se pierde ninguno.
 */
@Component
public class WorkerOutboxCatalogo {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerOutboxCatalogo.class);

    private final RepoOutboxCatalogo repoOutboxCatalogo;
    private final RabbitTemplate rabbit;
    private final PropiedadesOutbox propiedades;

    public WorkerOutboxCatalogo(
            RepoOutboxCatalogo repoOutboxCatalogo,
            RabbitTemplate rabbit,
            PropiedadesOutbox propiedades) {
        this.repoOutboxCatalogo = repoOutboxCatalogo;
        this.rabbit = rabbit;
        this.propiedades = propiedades;
    }

    /**
     * Pasos III.11 (→ RepoOutboxCatalogo obtenerPendientes) y III.12 (→ q.nuevaSubasta publicar).
     */
    @Scheduled(fixedDelayString = "${sso.outbox.intervalo:1s}")
    @Transactional
    public void publicarPendientes() {
        for (EventoPendiente evento :
                repoOutboxCatalogo.obtenerPendientes(propiedades.tamanoLote())) {
            try {
                publicar(evento);
                repoOutboxCatalogo.marcarEnviado(evento.idEvento());
            } catch (AmqpException e) {
                repoOutboxCatalogo.registrarIntento(evento.idEvento());
                LOG.warn(
                        "MOM no disponible; {} queda pendiente: {}",
                        evento.idEvento(),
                        e.getMessage());
                return;
            }
        }
    }

    /**
     * Publica en el exchange q.* con el nombre exacto del diagrama y espera la confirmación del
     * broker.
     */
    private void publicar(EventoPendiente evento) {
        Message mensaje =
                MessageBuilder.withBody(evento.payload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setMessageId(evento.idEvento().toString())
                        .setType(evento.tipo())
                        .setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT)
                        .build();
        rabbit.invoke(
                operaciones -> {
                    operaciones.send(evento.cola(), "", mensaje);
                    operaciones.waitForConfirmsOrDie(propiedades.esperaConfirmacion().toMillis());
                    return null;
                });
    }
}

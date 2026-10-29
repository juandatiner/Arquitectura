package co.edu.javeriana.sso.mscatalogo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.sso.mscatalogo.config.PropiedadesOutbox;
import co.edu.javeriana.sso.mscatalogo.modelo.EventoPendiente;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class WorkerOutboxCatalogoTest {

    @Test
    void siMomEstaCaidoElEventoQuedaPendiente() {
        RepoOutboxCatalogo repo = mock(RepoOutboxCatalogo.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UUID id = UUID.randomUUID();
        when(repo.obtenerPendientes(50))
                .thenReturn(
                        List.of(new EventoPendiente(id, "NuevaSubasta", "q.nuevaSubasta", "{}")));
        when(rabbit.invoke(any()))
                .thenThrow(new AmqpConnectException(new java.net.ConnectException()));

        new WorkerOutboxCatalogo(repo, rabbit, new PropiedadesOutbox(50, Duration.ofSeconds(1)))
                .publicarPendientes();

        verify(repo).registrarIntento(id);
        verify(repo, never()).marcarEnviado(any());
    }
}

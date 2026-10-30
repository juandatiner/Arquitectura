package co.edu.javeriana.sso.msnotificacion;

import co.edu.javeriana.sso.msnotificacion.modelo.Canal;
import co.edu.javeriana.sso.msnotificacion.modelo.Notificacion;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Envía cada notificación por correo y, si el evento lo pide, por SMS, sin repetir envíos. */
@Service
public class SrvNotificar {

    private static final Logger LOG = LoggerFactory.getLogger(SrvNotificar.class);
    private static final int LARGO_SMS = 300;

    private final RepoNotificar repoNotificar;
    private final IntegraMail integraMail;
    private final IntegraSMS integraSMS;

    public SrvNotificar(
            RepoNotificar repoNotificar, IntegraMail integraMail, IntegraSMS integraSMS) {
        this.repoNotificar = repoNotificar;
        this.integraMail = integraMail;
        this.integraSMS = integraSMS;
    }

    /**
     * Pasos IX.2 (ListenerNotificar → SrvNotificar), IX.3 (→ RepoNotificar yaEnviada), IX.4 (→
     * IntegraMail enviarCorreo), IX.6 (→ IntegraSMS enviarSMS, solo si el evento lo pide) e IX.8 (→
     * RepoNotificar registrarEnviada). Cada canal se registra apenas se envía: si el otro falla, el
     * reintento no repite el que ya salió.
     */
    public void notificar(Notificacion evento) {
        Set<Canal> enviados = repoNotificar.yaEnviada(evento.idEvento());
        String correo = evento.destinatario().correo();
        if (!enviados.contains(Canal.CORREO) && correo != null && !correo.isBlank()) {
            String resultado = integraMail.enviarCorreo(correo, evento.asunto(), evento.cuerpo());
            repoNotificar.registrarEnviada(evento.idEvento(), Canal.CORREO, resultado);
        }
        String celular = evento.destinatario().celular();
        if (evento.sms()
                && !enviados.contains(Canal.SMS)
                && celular != null
                && !celular.isBlank()) {
            String resultado = integraSMS.enviarSMS(celular, textoSms(evento));
            repoNotificar.registrarEnviada(evento.idEvento(), Canal.SMS, resultado);
        }
        LOG.info("Notificación {} ({}) procesada", evento.idEvento(), evento.origen());
    }

    private static String textoSms(Notificacion evento) {
        String texto = "SSO: " + evento.cuerpo();
        return texto.length() <= LARGO_SMS ? texto : texto.substring(0, LARGO_SMS - 1) + "…";
    }
}

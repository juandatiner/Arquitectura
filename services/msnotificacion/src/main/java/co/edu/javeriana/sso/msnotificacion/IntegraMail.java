package co.edu.javeriana.sso.msnotificacion;

import co.edu.javeriana.sso.msnotificacion.config.PropiedadesNotificacion;
import co.edu.javeriana.sso.msnotificacion.modelo.EnvioFallidoException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Integración con el servidor de correo (MAIL SERVER :1025) por SMTP. */
@Component
public class IntegraMail {

    private final JavaMailSender correo;
    private final String remitente;

    public IntegraMail(JavaMailSender correo, PropiedadesNotificacion propiedades) {
        this.correo = correo;
        this.remitente = propiedades.remitente();
    }

    /**
     * Pasos IX.4 (SrvNotificar → IntegraMail enviarCorreo) e IX.5 (→ CORREO enviar(mensaje), SMTP).
     */
    public String enviarCorreo(String destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        try {
            correo.send(mensaje);
            return "ENVIADO";
        } catch (MailException e) {
            throw new EnvioFallidoException("No se pudo enviar el correo a " + destinatario, e);
        }
    }
}

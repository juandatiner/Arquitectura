package co.edu.javeriana.sso.msacceso;

import co.edu.javeriana.sso.msacceso.config.PropiedadesAD;
import co.edu.javeriana.sso.msacceso.modelo.AdNoDisponibleException;
import co.edu.javeriana.sso.msacceso.modelo.CredencialesInvalidasException;
import java.util.Hashtable;
import java.util.regex.Pattern;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

/** Integración con AD (nodo AD SERVER :389, OpenLDAP) por LDAP. */
@Component
public class IntegraLoginAD {

    /** Evita inyección en el DN: solo caracteres válidos de un nombre de usuario. */
    private static final Pattern USUARIO_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final PropiedadesAD propiedadesAD;

    public IntegraLoginAD(PropiedadesAD propiedadesAD) {
        this.propiedadesAD = propiedadesAD;
    }

    /**
     * Pasos I.9 (SrvLogin → IntegraLoginAD) e I.10 (IntegraLoginAD → AD autenticar, LDAP bind).
     *
     * @return identidad validada (uid del usuario en AD)
     */
    public String validarCredenciales(String usuario, String clave) {
        // Un bind con clave vacía es anónimo y LDAP lo acepta: se rechaza antes.
        if (usuario == null
                || !USUARIO_VALIDO.matcher(usuario).matches()
                || clave == null
                || clave.isEmpty()) {
            throw new CredencialesInvalidasException();
        }
        Hashtable<String, String> entorno = new Hashtable<>();
        entorno.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        entorno.put(Context.PROVIDER_URL, propiedadesAD.url());
        entorno.put(Context.SECURITY_AUTHENTICATION, "simple");
        entorno.put(Context.SECURITY_PRINCIPAL, propiedadesAD.patronDn().replace("{0}", usuario));
        entorno.put(Context.SECURITY_CREDENTIALS, clave);
        entorno.put(
                "com.sun.jndi.ldap.connect.timeout",
                String.valueOf(propiedadesAD.timeout().toMillis()));
        entorno.put(
                "com.sun.jndi.ldap.read.timeout",
                String.valueOf(propiedadesAD.timeout().toMillis()));
        try {
            new InitialDirContext(entorno).close();
            return usuario;
        } catch (AuthenticationException e) {
            throw new CredencialesInvalidasException();
        } catch (NamingException e) {
            throw new AdNoDisponibleException(e);
        }
    }
}

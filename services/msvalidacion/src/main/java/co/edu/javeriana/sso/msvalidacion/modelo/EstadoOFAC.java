package co.edu.javeriana.sso.msvalidacion.modelo;

/** Formato de respuesta de la OFAC (enunciado). APROBADO y RECHAZADO son definitivos. */
public enum EstadoOFAC {
    APROBADO,
    RECHAZADO,
    ENPROCESO;

    public boolean definitivo() {
        return this != ENPROCESO;
    }
}

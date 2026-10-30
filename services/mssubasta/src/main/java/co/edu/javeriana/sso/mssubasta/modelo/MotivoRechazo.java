package co.edu.javeriana.sso.mssubasta.modelo;

/** Motivo de ofertaNoRegistrada(motivo) (paso IV.12b). */
public enum MotivoRechazo {
    /** Otra oferta cambió la versión de la subasta al mismo tiempo (filasAfectadas = 0). */
    CONFLICTO_CONCURRENTE,
    SUBASTA_NO_ABIERTA,
    FUERA_DE_PLAZO,
    MENOR_AL_MINIMO,
    NO_SUPERA_OFERTA_MAYOR
}

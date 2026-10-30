package co.edu.javeriana.sso.mssubasta.modelo;

/** Estado de la oferta (enunciado: "se manejará un estado de la oferta"). */
public enum EstadoOferta {
    /** Es la oferta mayor mientras la subasta está abierta. */
    REGISTRADA,
    /** Otra oferta la superó. */
    SUPERADA,
    /** Ganó la subasta al cierre (V.3). */
    GANADORA,
    /** No ganó la subasta al cierre (V.3). */
    PERDEDORA
}

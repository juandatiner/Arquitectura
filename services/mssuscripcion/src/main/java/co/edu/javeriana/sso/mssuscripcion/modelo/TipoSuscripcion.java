package co.edu.javeriana.sso.mssuscripcion.modelo;

/**
 * Enunciado: "El cliente puede suscribirse a nuevas subastas de bienes y a cambios en los valores
 * ofertados de los bienes para los cuales está ofertando".
 */
public enum TipoSuscripcion {
    /** valor = categoría del bien o {@link CriterioSuscripcion#TODAS}. */
    NUEVAS_SUBASTAS,
    /** valor = idSubasta. */
    CAMBIOS_OFERTA
}

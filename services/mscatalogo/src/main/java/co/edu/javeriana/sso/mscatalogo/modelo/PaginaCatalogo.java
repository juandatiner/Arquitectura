package co.edu.javeriana.sso.mscatalogo.modelo;

import java.util.List;

/** Resultado de consultarCatalogo: una página de subastas publicadas. */
public record PaginaCatalogo(
        List<SubastaPublicada> contenido, int pagina, int tamano, long total) {}

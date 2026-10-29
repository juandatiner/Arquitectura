package co.edu.javeriana.sso.mscatalogo.modelo;

/** Parámetro de consultarCatalogo(filtros) (pasos II.7a – II.9a). */
public record Filtros(String texto, String categoria, Integer pagina, Integer tamano) {

    public static final int TAMANO_MAXIMO = 100;

    public int paginaEfectiva() {
        return pagina == null || pagina < 0 ? 0 : pagina;
    }

    public int tamanoEfectivo() {
        return tamano == null || tamano < 1 ? 20 : Math.min(tamano, TAMANO_MAXIMO);
    }
}

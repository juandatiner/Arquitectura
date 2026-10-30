package co.edu.javeriana.sso.mssubasta.modelo;

import java.math.BigDecimal;

/** Una oferta registrada con el cliente que la hizo. */
public record Oferta(
        long idOferta, String idSubasta, Cliente cliente, BigDecimal valor, EstadoOferta estado) {}

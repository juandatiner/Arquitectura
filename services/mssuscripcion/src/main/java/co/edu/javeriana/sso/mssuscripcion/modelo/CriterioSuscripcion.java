package co.edu.javeriana.sso.mssuscripcion.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Parámetro {@code criterio} de suscribirse(criterio) (paso II.1). */
public record CriterioSuscripcion(@NotNull TipoSuscripcion tipo, @NotBlank String valor) {

    public static final String TODAS = "TODAS";
}

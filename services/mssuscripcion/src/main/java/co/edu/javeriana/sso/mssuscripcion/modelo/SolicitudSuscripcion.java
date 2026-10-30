package co.edu.javeriana.sso.mssuscripcion.modelo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de suscribirse(criterio, cliente) (paso II.7b). */
public record SolicitudSuscripcion(
        @NotNull @Valid CriterioSuscripcion criterio, @NotNull @Valid Cliente cliente) {}

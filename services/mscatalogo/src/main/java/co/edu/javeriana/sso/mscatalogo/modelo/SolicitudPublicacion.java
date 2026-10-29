package co.edu.javeriana.sso.mscatalogo.modelo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de publicarNuevaSubasta(idSubasta, admin) (paso III.7). */
public record SolicitudPublicacion(
        @NotBlank String idSubasta, @NotNull @Valid Administrador admin) {}

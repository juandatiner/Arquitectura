package co.edu.javeriana.sso.mssuscripcion.modelo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Cuerpo de cancelarSuscripcion(idSuscripcion, cliente) (paso II.7b). */
public record SolicitudCancelacion(@NotNull UUID idSuscripcion, @NotNull @Valid Cliente cliente) {}

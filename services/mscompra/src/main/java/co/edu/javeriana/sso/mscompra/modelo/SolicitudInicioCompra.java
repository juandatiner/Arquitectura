package co.edu.javeriana.sso.mscompra.modelo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de iniciarCompra(numeroCompra, cliente) (paso VI.8). */
public record SolicitudInicioCompra(
        @NotBlank String numeroCompra, @NotNull @Valid Cliente cliente) {}

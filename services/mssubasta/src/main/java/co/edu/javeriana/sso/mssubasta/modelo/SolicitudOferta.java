package co.edu.javeriana.sso.mssubasta.modelo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Cuerpo de registrarOferta(idSubasta, cliente, valor) (paso IV.7). */
public record SolicitudOferta(
        @NotBlank String idSubasta,
        @NotNull @Valid Cliente cliente,
        @NotNull @Positive BigDecimal valor) {}

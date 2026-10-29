package co.edu.javeriana.sso.msacceso.modelo;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de autenticar(usuario, clave): mismos parámetros y orden que el paso I.7. */
public record Credenciales(@NotBlank String usuario, @NotBlank String clave) {

    @Override
    public String toString() {
        return "Credenciales[usuario=" + usuario + ", clave=****]";
    }
}

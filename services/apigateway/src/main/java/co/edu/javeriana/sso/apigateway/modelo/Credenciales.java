package co.edu.javeriana.sso.apigateway.modelo;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de autenticar(usuario, clave) (pasos I.4 e I.7). */
public record Credenciales(@NotBlank String usuario, @NotBlank String clave) {

    @Override
    public String toString() {
        return "Credenciales[usuario=" + usuario + ", clave=****]";
    }
}

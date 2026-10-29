package co.edu.javeriana.sso.msacceso.config;

import co.edu.javeriana.sso.msacceso.modelo.AdNoDisponibleException;
import co.edu.javeriana.sso.msacceso.modelo.ClienteNoRegistradoException;
import co.edu.javeriana.sso.msacceso.modelo.CredencialesInvalidasException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce los errores de autenticación a respuestas HTTP (RFC 9457). */
@RestControllerAdvice
public class ManejoErrores {

    @ExceptionHandler(CredencialesInvalidasException.class)
    ProblemDetail credencialesInvalidas(CredencialesInvalidasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ClienteNoRegistradoException.class)
    ProblemDetail clienteNoRegistrado(ClienteNoRegistradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(AdNoDisponibleException.class)
    ProblemDetail adNoDisponible(AdNoDisponibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}

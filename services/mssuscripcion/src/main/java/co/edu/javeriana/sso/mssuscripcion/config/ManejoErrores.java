package co.edu.javeriana.sso.mssuscripcion.config;

import co.edu.javeriana.sso.mssuscripcion.modelo.SuscripcionNoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejoErrores {

    @ExceptionHandler(SuscripcionNoEncontradaException.class)
    ProblemDetail noEncontrada(SuscripcionNoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}

package co.edu.javeriana.sso.msvalidacion.config;

import co.edu.javeriana.sso.msvalidacion.modelo.OfacNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejoErrores {

    @ExceptionHandler(OfacNoDisponibleException.class)
    ProblemDetail ofacNoDisponible(OfacNoDisponibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}

package co.edu.javeriana.sso.mscompra.config;

import co.edu.javeriana.sso.mscompra.modelo.CompraAjenaException;
import co.edu.javeriana.sso.mscompra.modelo.CompraNoEncontradaException;
import co.edu.javeriana.sso.mscompra.modelo.CompraNoIniciableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejoErrores {

    @ExceptionHandler(CompraNoEncontradaException.class)
    ProblemDetail noEncontrada(CompraNoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CompraAjenaException.class)
    ProblemDetail ajena(CompraAjenaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(CompraNoIniciableException.class)
    ProblemDetail noIniciable(CompraNoIniciableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}

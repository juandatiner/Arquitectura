package co.edu.javeriana.sso.mscatalogo.config;

import co.edu.javeriana.sso.mscatalogo.modelo.SubastaNoEncontradaException;
import co.edu.javeriana.sso.mscatalogo.modelo.SubastaNoPublicableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejoErrores {

    @ExceptionHandler(SubastaNoEncontradaException.class)
    ProblemDetail noEncontrada(SubastaNoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SubastaNoPublicableException.class)
    ProblemDetail noPublicable(SubastaNoPublicableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}

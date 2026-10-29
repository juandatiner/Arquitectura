package co.edu.javeriana.sso.apigateway.config;

import co.edu.javeriana.sso.apigateway.modelo.AccesoDenegadoException;
import co.edu.javeriana.sso.apigateway.modelo.ServicioNoDisponibleException;
import co.edu.javeriana.sso.apigateway.modelo.TokenInvalidoException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores propios del gateway (RFC 9457). Los errores de los componentes internos pasan tal cual.
 */
@RestControllerAdvice
public class ManejoErrores {

    @ExceptionHandler(TokenInvalidoException.class)
    ResponseEntity<ProblemDetail> tokenInvalido(TokenInvalidoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    ProblemDetail accesoDenegado(AccesoDenegadoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    ProblemDetail servicioNoDisponible(ServicioNoDisponibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}

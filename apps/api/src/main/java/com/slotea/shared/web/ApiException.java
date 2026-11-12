package com.slotea.shared.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Error de negocio que Spring MVC devuelve como {@code ProblemDetail} (RFC 9457). */
public class ApiException extends ErrorResponseException {

    private ApiException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * 400 con el mismo formato que la validación de Bean Validation ({@code errors: [{field, message}]}),
     * para reglas que miran varios campos a la vez (p. ej. tramos de horario solapados).
     */
    public static ApiException invalidField(String field, String message) {
        var ex = badRequest("La petición tiene datos no válidos.");
        ex.getBody().setProperty("errors", List.of(Map.of("field", field, "message", message)));
        return ex;
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, detail);
    }

    /** También para recursos de otro negocio: no revelamos que existen. */
    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail);
    }
}

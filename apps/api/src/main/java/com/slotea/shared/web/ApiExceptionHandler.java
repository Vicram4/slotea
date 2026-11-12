package com.slotea.shared.web;

import java.util.List;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos los errores salen como {@code ProblemDetail}. Añade la lista de campos inválidos (400)
 * y traduce las restricciones UNIQUE conocidas a 409 (por si dos peticiones compiten).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Restricción de la BD → mensaje para el usuario. */
    private static final Map<String, String> CONFLICTS = Map.of(
            "app_user_email_key", "Ya existe una cuenta con ese email.",
            "business_slug_key", "Esa dirección ya está en uso. Prueba con otra.");

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail("La petición tiene datos no válidos.");
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                String message = CONFLICTS.get(cve.getConstraintName());
                if (message != null) {
                    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
                }
            }
        }
        throw ex;
    }

    record FieldError(String field, @Nullable String message) {
    }
}

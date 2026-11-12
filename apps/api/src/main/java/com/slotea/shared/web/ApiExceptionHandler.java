package com.slotea.shared.web;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
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
 * y traduce las restricciones conocidas de la BD (UNIQUE, EXCLUDE) a 409: la aplicación valida
 * antes, pero si dos peticiones compiten, la última palabra la tiene PostgreSQL.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Restricción de la BD → mensaje para el usuario. */
    private static final Map<String, String> CONFLICTS = Map.of(
            "app_user_email_key", "Ya existe una cuenta con ese email.",
            "business_slug_key", "Esa dirección ya está en uso. Prueba con otra.",
            "service_business_name_key", "Ya tienes un servicio con ese nombre.",
            "staff_member_business_user_key", "Ese usuario ya tiene ficha de profesional.",
            "working_hours_no_overlap", "Los tramos del horario se solapan.",
            // Normalmente lo captura BookingService (para probar con otro profesional); esto es la red de seguridad.
            "appointment_no_overlap", "Ese hueco acaba de ocuparse. Elige otra hora.");

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

    /**
     * Hibernate no traduce todas las violaciones a {@code ConstraintViolationException} (las de
     * {@code EXCLUDE}, SQLState 23P01, no): ver {@link DatabaseErrors}.
     */
    @ExceptionHandler
    ProblemDetail handleDataAccess(DataAccessException ex) throws DataAccessException {
        String message = CONFLICTS.get(DatabaseErrors.violatedConstraint(ex));
        if (message == null) {
            throw ex;
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
    }

    record FieldError(String field, @Nullable String message) {
    }
}

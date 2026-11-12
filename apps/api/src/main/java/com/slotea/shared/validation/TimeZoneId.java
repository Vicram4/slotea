package com.slotea.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Zona horaria IANA válida ({@code Europe/Madrid}). {@code null} se considera válido. */
@Documented
@Constraint(validatedBy = TimeZoneId.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeZoneId {

    String message() default "no es una zona horaria válida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TimeZoneId, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // Solo ids de región (no offsets fijos como "+02:00"): el cambio de hora tiene que funcionar.
            return value == null || (value.contains("/") && ZoneId.getAvailableZoneIds().contains(value));
        }
    }
}

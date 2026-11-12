package com.slotea.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** El número es múltiplo de {@link #value()} (p. ej. minutos de 5 en 5). {@code null} se considera válido. */
@Documented
@Constraint(validatedBy = MultipleOf.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface MultipleOf {

    int value();

    String message() default "debe ser múltiplo de {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MultipleOf, Integer> {

        private int step;

        @Override
        public void initialize(MultipleOf annotation) {
            this.step = annotation.value();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || value % step == 0;
        }
    }
}

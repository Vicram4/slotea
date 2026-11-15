package com.slotea.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Color de marca de un negocio: hexadecimal {@code #rrggbb} con un contraste de al menos 4,5:1
 * frente al blanco (WCAG AA). Así sirve de fondo de un botón con texto blanco y de texto o borde
 * sobre la página blanca. {@code null} se considera válido.
 */
@Documented
@Constraint(validatedBy = BrandColor.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface BrandColor {

    /** El que tiene un negocio que no ha elegido ninguno (el mismo que pone la migración V8). */
    String DEFAULT = "#18181b";

    /** WCAG 2.2, criterio 1.4.3 (texto normal, nivel AA). */
    double MIN_CONTRAST = 4.5;

    String message() default "tiene que ser un color #rrggbb que se lea bien sobre blanco";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BrandColor, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Colors.isAcceptable(value);
        }
    }

    /** Las operaciones, aparte de la anotación, para usarlas también fuera de un DTO. */
    final class Colors {

        private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

        private Colors() {
        }

        public static boolean isHex(String value) {
            return HEX.matcher(value).matches();
        }

        public static boolean isAcceptable(String value) {
            return isHex(value) && contrastWithWhite(value) >= MIN_CONTRAST;
        }

        /** Contraste WCAG entre el color y el blanco: de 1 (blanco) a 21 (negro). */
        public static double contrastWithWhite(String hex) {
            if (!isHex(hex)) {
                throw new IllegalArgumentException("Not a #rrggbb color: " + hex);
            }
            int rgb = Integer.parseInt(hex.substring(1), 16);
            double luminance = 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
            return 1.05 / (luminance + 0.05);
        }

        /** sRGB → luminancia lineal de un canal (0-255). */
        private static double channel(int value) {
            double c = (value & 0xff) / 255.0;
            return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }
}

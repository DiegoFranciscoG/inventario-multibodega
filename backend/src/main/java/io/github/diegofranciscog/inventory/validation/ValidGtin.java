package io.github.diegofranciscog.inventory.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** GTIN-8/12/13/14 con dígito verificador GS1 válido (R-11). {@code null} o vacío se consideran válidos. */
@Documented
@Constraint(validatedBy = GtinValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGtin {

    String message() default "GTIN inválido: debe tener 8, 12, 13 o 14 dígitos y dígito verificador GS1 correcto";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

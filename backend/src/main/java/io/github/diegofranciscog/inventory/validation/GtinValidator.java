package io.github.diegofranciscog.inventory.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import io.github.diegofranciscog.inventory.gs1.Gtin;

public class GtinValidator implements ConstraintValidator<ValidGtin, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || Gtin.isValid(value);
    }
}

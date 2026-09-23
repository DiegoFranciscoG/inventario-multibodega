package io.github.diegofranciscog.inventory.exception;

import org.springframework.http.HttpStatus;

/** Violación de una regla de negocio (HTTP 422). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}

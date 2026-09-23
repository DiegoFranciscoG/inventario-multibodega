package io.github.diegofranciscog.inventory.exception;

import org.springframework.http.HttpStatus;

/** Excepción de negocio que se traduce a una respuesta RFC 9457 (ProblemDetail) con un código estable. */
public abstract class ApiException extends RuntimeException {

    private final String code;

    protected ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public abstract HttpStatus getStatus();
}

package io.github.diegofranciscog.inventory.exception;

import org.springframework.http.HttpStatus;

/** Conflicto de datos: duplicados o conflicto de concurrencia que no se resolvió con reintentos (HTTP 409). */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}

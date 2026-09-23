package io.github.diegofranciscog.inventory.exception;

import org.springframework.http.HttpStatus;

/** El usuario está autenticado pero no puede operar sobre ese recurso (BOLA, segregación de funciones). */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}

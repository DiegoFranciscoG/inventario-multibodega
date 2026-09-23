package io.github.diegofranciscog.inventory.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", "%s no encontrado: %s".formatted(resource, id));
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}

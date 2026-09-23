package io.github.diegofranciscog.inventory.exception;

import org.springframework.http.HttpStatus;

/** Límite de intentos superado (rate limiting del login). */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("TOO_MANY_REQUESTS", "Demasiados intentos. Vuelve a intentarlo en unos segundos.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}

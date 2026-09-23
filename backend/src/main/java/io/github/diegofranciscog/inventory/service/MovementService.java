package io.github.diegofranciscog.inventory.service;

import jakarta.persistence.OptimisticLockException;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementResponse;

/**
 * Punto de entrada de los movimientos. Cada intento corre en una transacción nueva de {@link MovementPostingService};
 * si otra transacción modificó el mismo stock o costo ({@code @Version}) o hubo un interbloqueo, se reintenta desde cero
 * con los datos ya confirmados (R-10). Tras 4 reintentos el conflicto llega al cliente como HTTP 409.
 */
@Service
public class MovementService {

    private final MovementPostingService posting;
    private final MovementQueryService queries;

    public MovementService(MovementPostingService posting, MovementQueryService queries) {
        this.posting = posting;
        this.queries = queries;
    }

    @Retryable(includes = {ConcurrencyFailureException.class, OptimisticLockException.class},
            maxRetries = 4, delay = 25, jitter = 25, multiplier = 2, maxDelay = 400)
    public MovementResponse receive(ReceiptRequest request, Long userId) {
        return queries.get(posting.receive(request, userId, null).getId());
    }

    @Retryable(includes = {ConcurrencyFailureException.class, OptimisticLockException.class},
            maxRetries = 4, delay = 25, jitter = 25, multiplier = 2, maxDelay = 400)
    public MovementResponse issue(IssueRequest request, Long userId) {
        return queries.get(posting.issue(request, userId, null).getId());
    }

    @Retryable(includes = {ConcurrencyFailureException.class, OptimisticLockException.class},
            maxRetries = 4, delay = 25, jitter = 25, multiplier = 2, maxDelay = 400)
    public MovementResponse transfer(TransferRequest request, Long userId) {
        return queries.get(posting.transfer(request, userId, null).getId());
    }

    @Retryable(includes = {ConcurrencyFailureException.class, OptimisticLockException.class},
            maxRetries = 4, delay = 25, jitter = 25, multiplier = 2, maxDelay = 400)
    public MovementResponse adjust(AdjustmentRequest request, Long userId) {
        return queries.get(posting.adjust(request, userId, null, null).getId());
    }
}

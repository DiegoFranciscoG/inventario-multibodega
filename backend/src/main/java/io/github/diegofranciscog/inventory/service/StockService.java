package io.github.diegofranciscog.inventory.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LotResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.StockResponse;
import io.github.diegofranciscog.inventory.mapper.StorageMapper;
import io.github.diegofranciscog.inventory.repository.LotRepository;
import io.github.diegofranciscog.inventory.repository.StockRepository;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stocks;
    private final LotRepository lots;
    private final ReferenceResolver references;
    private final AppProperties properties;
    private final Clock clock;

    public StockService(StockRepository stocks, LotRepository lots, ReferenceResolver references,
                        AppProperties properties, Clock clock) {
        this.stocks = stocks;
        this.lots = lots;
        this.references = references;
        this.properties = properties;
        this.clock = clock;
    }

    public PageResponse<StockResponse> search(Long warehouseId, Long productId, Long locationId, boolean includeZero,
                                              int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Pagination.clamp(size, properties),
                Sort.by("warehouse.code", "location.code", "product.sku"));
        return PageResponse.from(stocks.search(warehouseId, productId, locationId, !includeZero, pageable),
                StorageMapper::toResponse);
    }

    public List<LotResponse> lots(Long productId) {
        references.product(productId);
        LocalDate today = LocalDate.now(clock.withZone(properties.inventory().timeZone()));
        return lots.findByProductIdOrderByExpiryDateAsc(productId).stream()
                .map(lot -> StorageMapper.toResponse(lot, today))
                .toList();
    }
}

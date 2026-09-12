package com.saed.backend.person.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConvivienteLimitExceededException extends RuntimeException {
    private final Long unitId;
    private final int limit;
    private final int currentCount;

    public ConvivienteLimitExceededException(Long unitId, int limit, int currentCount) {
        super(String.format("Límite máximo de convivientes alcanzado para esta unidad (límite: %d, activos: %d)", limit, currentCount));
        this.unitId = unitId;
        this.limit = limit;
        this.currentCount = currentCount;
    }

    public Long getUnitId() {
        return unitId;
    }

    public int getLimit() {
        return limit;
    }

    public int getCurrentCount() {
        return currentCount;
    }
}

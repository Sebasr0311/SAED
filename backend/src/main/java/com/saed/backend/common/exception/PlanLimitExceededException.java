package com.saed.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PlanLimitExceededException extends RuntimeException {

    private final String limitType;
    private final long currentCount;
    private final long maxLimit;

    public PlanLimitExceededException(String message) {
        super(message);
        this.limitType = "GENERAL";
        this.currentCount = 0;
        this.maxLimit = 0;
    }

    public PlanLimitExceededException(String limitType, long currentCount, long maxLimit) {
        super(String.format("Límite de %s alcanzado para el plan actual (%d/%d). Actualice su suscripción para continuar.",
                limitType, currentCount, maxLimit));
        this.limitType = limitType;
        this.currentCount = currentCount;
        this.maxLimit = maxLimit;
    }

    public String getLimitType() {
        return limitType;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public long getMaxLimit() {
        return maxLimit;
    }
}

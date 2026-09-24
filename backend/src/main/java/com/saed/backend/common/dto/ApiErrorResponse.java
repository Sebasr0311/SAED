package com.saed.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.*;

/**
 * ApiErrorResponse — Canonical Error Response Contract v1 for SAED 2.0.
 *
 * Guarantees determinism, security (zero stack traces or raw SQL leak),
 * frontend-friendly code-based handling, and strict correlation/traceability.
 *
 * Standard JSON payload:
 * {
 *   "success": false,
 *   "status": 400,
 *   "code": "VALIDATION_FAILED",
 *   "message": "Error de validación en los campos enviados",
 *   "traceId": "1a2b3c4d-...",
 *   "timestamp": "2026-09-19T14:40:00.000Z",
 *   "fieldErrors": [ ... ],
 *   "errors": { ... },
 *   "details": { ... }
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private boolean success = false;
    private int status;
    private String code;
    private String message;
    private String traceId;
    private String timestamp;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<FieldErrorDetail> fieldErrors;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> errors;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> details;

    private final Map<String, Object> extraProperties = new LinkedHashMap<>();

    public ApiErrorResponse() {
        this.timestamp = Instant.now().toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<FieldErrorDetail> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraProperties() {
        return extraProperties;
    }

    @JsonAnySetter
    public void setExtraProperty(String key, Object value) {
        if (key != null && value != null) {
            this.extraProperties.put(key, value);
        }
    }

    public static class Builder {
        private final ApiErrorResponse response = new ApiErrorResponse();

        public Builder status(int status) {
            response.setStatus(status);
            return this;
        }

        public Builder code(String code) {
            response.setCode(code);
            return this;
        }

        public Builder message(String message) {
            response.setMessage(message);
            return this;
        }

        public Builder traceId(String traceId) {
            response.setTraceId(traceId);
            return this;
        }

        public Builder timestamp(String timestamp) {
            response.setTimestamp(timestamp);
            return this;
        }

        public Builder fieldErrors(List<FieldErrorDetail> fieldErrors) {
            response.setFieldErrors(fieldErrors);
            return this;
        }

        public Builder errors(Map<String, String> errors) {
            response.setErrors(errors);
            return this;
        }

        public Builder details(Map<String, Object> details) {
            response.setDetails(details);
            return this;
        }

        public Builder extra(String key, Object value) {
            response.setExtraProperty(key, value);
            return this;
        }

        public ApiErrorResponse build() {
            return response;
        }
    }
}

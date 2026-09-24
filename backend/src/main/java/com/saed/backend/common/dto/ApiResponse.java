package com.saed.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saed.backend.audit.CorrelationIdHolder;

import java.time.Instant;
import java.util.Map;

/**
 * ApiResponse — Canonical Success & General Response Wrapper for SAED 2.0.
 *
 * Provides a consistent response schema:
 * {
 *   "success": true,
 *   "status": "success",
 *   "data": { ... },
 *   "message": "...",
 *   "traceId": "...",
 *   "timestamp": "..."
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String status;
    private T data;
    private String message;
    private String code;
    private String traceId;
    private String timestamp;
    private Map<String, Object> meta;

    public ApiResponse() {
        this.timestamp = Instant.now().toString();
        this.traceId = CorrelationIdHolder.get();
    }

    public ApiResponse(String status, T data) {
        this();
        this.status = status;
        this.data = data;
        this.success = "success".equalsIgnoreCase(status);
    }
    
    public ApiResponse(String status, String message) {
        this();
        this.status = status;
        this.message = message;
        this.success = "success".equalsIgnoreCase(status);
        if (!this.success && this.code == null) {
            this.code = "BAD_REQUEST";
        }
    }

    public ApiResponse(String status, T data, String message) {
        this(status, data);
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("success", data, message);
    }

    public static <T> ApiResponse<T> success(T data, Map<String, Object> meta) {
        ApiResponse<T> resp = new ApiResponse<>("success", data);
        resp.setMeta(meta);
        return resp;
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> resp = new ApiResponse<>("error", message);
        resp.setCode(code);
        return resp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status; 
        this.success = "success".equalsIgnoreCase(status);
    }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
}

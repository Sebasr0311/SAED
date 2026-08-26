package com.saed.backend.common.dto;

public class ApiResponse<T> {
    private String status;
    private T data;
    private String message;

    public ApiResponse() {}

    public ApiResponse(String status, T data) {
        this.status = status;
        this.data = data;
    }
    
    public ApiResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message);
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

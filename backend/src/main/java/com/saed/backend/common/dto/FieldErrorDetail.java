package com.saed.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FieldErrorDetail - Granular field-level validation error descriptor.
 * Follows the SAED 2.0 API Response & Error Contract v1.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldErrorDetail {

    private String field;
    private String code;
    private String message;

    public FieldErrorDetail() {
    }

    public FieldErrorDetail(String field, String code, String message) {
        this.field = field;
        this.code = code;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
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
}

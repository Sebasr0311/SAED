package com.saed.backend.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaedContext {
    private Long userId;
    private Long organizationId;
    private Long propertyId;
    private Long unitId;
    private String roleCode;
}

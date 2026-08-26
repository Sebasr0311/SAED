package com.saed.backend.context;

public class SaedContext {
    private Long userId;
    private Long organizationId;
    private Long propertyId;
    private Long unitId;
    private String roleCode;

    public SaedContext() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SaedContext ctx = new SaedContext();
        public Builder userId(Long userId) { ctx.userId = userId; return this; }
        public Builder organizationId(Long organizationId) { ctx.organizationId = organizationId; return this; }
        public Builder propertyId(Long propertyId) { ctx.propertyId = propertyId; return this; }
        public Builder unitId(Long unitId) { ctx.unitId = unitId; return this; }
        public Builder roleCode(String roleCode) { ctx.roleCode = roleCode; return this; }
        public SaedContext build() { return ctx; }
    }
}

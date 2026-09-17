package com.saed.backend.platform.dto;

/**
 * DTO representing the organizational storage quota, consumption, and limits.
 */
public class StorageQuotaDTO {

    private Long organizationId;
    private long limitBytes;
    private long usedBytes;
    private long availableBytes;
    private double limitGb;
    private double usedGb;
    private double availableGb;
    private double percentageUsed;
    private String planCodigo;
    private String estadoMembresia;

    public StorageQuotaDTO() {}

    public StorageQuotaDTO(Long organizationId, long limitBytes, long usedBytes,
                           long availableBytes, double limitGb, double usedGb,
                           double availableGb, double percentageUsed,
                           String planCodigo, String estadoMembresia) {
        this.organizationId = organizationId;
        this.limitBytes = limitBytes;
        this.usedBytes = usedBytes;
        this.availableBytes = availableBytes;
        this.limitGb = limitGb;
        this.usedGb = usedGb;
        this.availableGb = availableGb;
        this.percentageUsed = percentageUsed;
        this.planCodigo = planCodigo;
        this.estadoMembresia = estadoMembresia;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public long getLimitBytes() {
        return limitBytes;
    }

    public void setLimitBytes(long limitBytes) {
        this.limitBytes = limitBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    public void setAvailableBytes(long availableBytes) {
        this.availableBytes = availableBytes;
    }

    public double getLimitGb() {
        return limitGb;
    }

    public void setLimitGb(double limitGb) {
        this.limitGb = limitGb;
    }

    public double getUsedGb() {
        return usedGb;
    }

    public void setUsedGb(double usedGb) {
        this.usedGb = usedGb;
    }

    public double getAvailableGb() {
        return availableGb;
    }

    public void setAvailableGb(double availableGb) {
        this.availableGb = availableGb;
    }

    public double getPercentageUsed() {
        return percentageUsed;
    }

    public void setPercentageUsed(double percentageUsed) {
        this.percentageUsed = percentageUsed;
    }

    public String getPlanCodigo() {
        return planCodigo;
    }

    public void setPlanCodigo(String planCodigo) {
        this.planCodigo = planCodigo;
    }

    public String getEstadoMembresia() {
        return estadoMembresia;
    }

    public void setEstadoMembresia(String estadoMembresia) {
        this.estadoMembresia = estadoMembresia;
    }
}

package com.saed.backend.emergencias.dto;

public class EmergenciasSummaryDTO {
    private int totalPlanes;
    private int planesActivos;
    private int totalContactos;
    private int contactosPrioritarios;

    public EmergenciasSummaryDTO() {}

    public EmergenciasSummaryDTO(int totalPlanes, int planesActivos, int totalContactos, int contactosPrioritarios) {
        this.totalPlanes = totalPlanes;
        this.planesActivos = planesActivos;
        this.totalContactos = totalContactos;
        this.contactosPrioritarios = contactosPrioritarios;
    }

    public int getTotalPlanes() { return totalPlanes; }
    public void setTotalPlanes(int totalPlanes) { this.totalPlanes = totalPlanes; }

    public int getPlanesActivos() { return planesActivos; }
    public void setPlanesActivos(int planesActivos) { this.planesActivos = planesActivos; }

    public int getTotalContactos() { return totalContactos; }
    public void setTotalContactos(int totalContactos) { this.totalContactos = totalContactos; }

    public int getContactosPrioritarios() { return contactosPrioritarios; }
    public void setContactosPrioritarios(int contactosPrioritarios) { this.contactosPrioritarios = contactosPrioritarios; }
}

package com.saed.backend.asambleas.dto;

import java.math.BigDecimal;

public class QuorumLiveDTO {
    private Long idAsamblea;
    private BigDecimal quorumRequeridoPct;
    private BigDecimal quorumAlcanzadoPct;
    private Boolean tieneQuorum;
    private Integer totalUnidadesRegistradas;
    private BigDecimal totalCoeficienteRegistrado;
    private String estadoAsamblea;

    public QuorumLiveDTO() {}

    public QuorumLiveDTO(Long idAsamblea, BigDecimal quorumRequeridoPct, BigDecimal quorumAlcanzadoPct,
                         Boolean tieneQuorum, Integer totalUnidadesRegistradas,
                         BigDecimal totalCoeficienteRegistrado, String estadoAsamblea) {
        this.idAsamblea = idAsamblea;
        this.quorumRequeridoPct = quorumRequeridoPct;
        this.quorumAlcanzadoPct = quorumAlcanzadoPct;
        this.tieneQuorum = tieneQuorum;
        this.totalUnidadesRegistradas = totalUnidadesRegistradas;
        this.totalCoeficienteRegistrado = totalCoeficienteRegistrado;
        this.estadoAsamblea = estadoAsamblea;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idAsamblea;
        private BigDecimal quorumRequeridoPct;
        private BigDecimal quorumAlcanzadoPct;
        private Boolean tieneQuorum;
        private Integer totalUnidadesRegistradas;
        private BigDecimal totalCoeficienteRegistrado;
        private String estadoAsamblea;

        public Builder idAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; return this; }
        public Builder quorumRequeridoPct(BigDecimal quorumRequeridoPct) { this.quorumRequeridoPct = quorumRequeridoPct; return this; }
        public Builder quorumAlcanzadoPct(BigDecimal quorumAlcanzadoPct) { this.quorumAlcanzadoPct = quorumAlcanzadoPct; return this; }
        public Builder tieneQuorum(Boolean tieneQuorum) { this.tieneQuorum = tieneQuorum; return this; }
        public Builder totalUnidadesRegistradas(Integer totalUnidadesRegistradas) { this.totalUnidadesRegistradas = totalUnidadesRegistradas; return this; }
        public Builder totalCoeficienteRegistrado(BigDecimal totalCoeficienteRegistrado) { this.totalCoeficienteRegistrado = totalCoeficienteRegistrado; return this; }
        public Builder estadoAsamblea(String estadoAsamblea) { this.estadoAsamblea = estadoAsamblea; return this; }

        public QuorumLiveDTO build() {
            return new QuorumLiveDTO(idAsamblea, quorumRequeridoPct, quorumAlcanzadoPct, tieneQuorum,
                    totalUnidadesRegistradas, totalCoeficienteRegistrado, estadoAsamblea);
        }
    }

    public Long getIdAsamblea() { return idAsamblea; }
    public void setIdAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; }

    public BigDecimal getQuorumRequeridoPct() { return quorumRequeridoPct; }
    public void setQuorumRequeridoPct(BigDecimal quorumRequeridoPct) { this.quorumRequeridoPct = quorumRequeridoPct; }

    public BigDecimal getQuorumAlcanzadoPct() { return quorumAlcanzadoPct; }
    public void setQuorumAlcanzadoPct(BigDecimal quorumAlcanzadoPct) { this.quorumAlcanzadoPct = quorumAlcanzadoPct; }

    public Boolean getTieneQuorum() { return tieneQuorum; }
    public void setTieneQuorum(Boolean tieneQuorum) { this.tieneQuorum = tieneQuorum; }

    public Integer getTotalUnidadesRegistradas() { return totalUnidadesRegistradas; }
    public void setTotalUnidadesRegistradas(Integer totalUnidadesRegistradas) { this.totalUnidadesRegistradas = totalUnidadesRegistradas; }

    public BigDecimal getTotalCoeficienteRegistrado() { return totalCoeficienteRegistrado; }
    public void setTotalCoeficienteRegistrado(BigDecimal totalCoeficienteRegistrado) { this.totalCoeficienteRegistrado = totalCoeficienteRegistrado; }

    public String getEstadoAsamblea() { return estadoAsamblea; }
    public void setEstadoAsamblea(String estadoAsamblea) { this.estadoAsamblea = estadoAsamblea; }
}

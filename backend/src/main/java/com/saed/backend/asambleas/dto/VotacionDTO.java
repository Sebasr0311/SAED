package com.saed.backend.asambleas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class VotacionDTO {
    private Long idVotacion;
    private Long idAsamblea;
    private Integer puntoOrdenDia;
    private String titulo;
    private String descripcion;
    private String tipoMayoriaRequerida;
    private OffsetDateTime horaApertura;
    private OffsetDateTime horaCierre;
    private String estado;
    private BigDecimal votosSi;
    private BigDecimal votosNo;
    private BigDecimal votosBlanco;
    private BigDecimal votosAbstencion;
    private Integer totalVotos;
    private Boolean aprobada;

    public VotacionDTO() {}

    public VotacionDTO(Long idVotacion, Long idAsamblea, Integer puntoOrdenDia, String titulo,
                       String descripcion, String tipoMayoriaRequerida, OffsetDateTime horaApertura,
                       OffsetDateTime horaCierre, String estado, BigDecimal votosSi,
                       BigDecimal votosNo, BigDecimal votosBlanco, BigDecimal votosAbstencion,
                       Integer totalVotos, Boolean aprobada) {
        this.idVotacion = idVotacion;
        this.idAsamblea = idAsamblea;
        this.puntoOrdenDia = puntoOrdenDia;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tipoMayoriaRequerida = tipoMayoriaRequerida;
        this.horaApertura = horaApertura;
        this.horaCierre = horaCierre;
        this.estado = estado;
        this.votosSi = votosSi;
        this.votosNo = votosNo;
        this.votosBlanco = votosBlanco;
        this.votosAbstencion = votosAbstencion;
        this.totalVotos = totalVotos;
        this.aprobada = aprobada;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idVotacion;
        private Long idAsamblea;
        private Integer puntoOrdenDia;
        private String titulo;
        private String descripcion;
        private String tipoMayoriaRequerida;
        private OffsetDateTime horaApertura;
        private OffsetDateTime horaCierre;
        private String estado;
        private BigDecimal votosSi;
        private BigDecimal votosNo;
        private BigDecimal votosBlanco;
        private BigDecimal votosAbstencion;
        private Integer totalVotos;
        private Boolean aprobada;

        public Builder idVotacion(Long idVotacion) { this.idVotacion = idVotacion; return this; }
        public Builder idAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; return this; }
        public Builder puntoOrdenDia(Integer puntoOrdenDia) { this.puntoOrdenDia = puntoOrdenDia; return this; }
        public Builder titulo(String titulo) { this.titulo = titulo; return this; }
        public Builder descripcion(String descripcion) { this.descripcion = descripcion; return this; }
        public Builder tipoMayoriaRequerida(String tipoMayoriaRequerida) { this.tipoMayoriaRequerida = tipoMayoriaRequerida; return this; }
        public Builder horaApertura(OffsetDateTime horaApertura) { this.horaApertura = horaApertura; return this; }
        public Builder horaCierre(OffsetDateTime horaCierre) { this.horaCierre = horaCierre; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder votosSi(BigDecimal votosSi) { this.votosSi = votosSi; return this; }
        public Builder votosNo(BigDecimal votosNo) { this.votosNo = votosNo; return this; }
        public Builder votosBlanco(BigDecimal votosBlanco) { this.votosBlanco = votosBlanco; return this; }
        public Builder votosAbstencion(BigDecimal votosAbstencion) { this.votosAbstencion = votosAbstencion; return this; }
        public Builder totalVotos(Integer totalVotos) { this.totalVotos = totalVotos; return this; }
        public Builder aprobada(Boolean aprobada) { this.aprobada = aprobada; return this; }

        public VotacionDTO build() {
            return new VotacionDTO(idVotacion, idAsamblea, puntoOrdenDia, titulo, descripcion,
                    tipoMayoriaRequerida, horaApertura, horaCierre, estado, votosSi, votosNo,
                    votosBlanco, votosAbstencion, totalVotos, aprobada);
        }
    }

    public Long getIdVotacion() { return idVotacion; }
    public void setIdVotacion(Long idVotacion) { this.idVotacion = idVotacion; }

    public Long getIdAsamblea() { return idAsamblea; }
    public void setIdAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; }

    public Integer getPuntoOrdenDia() { return puntoOrdenDia; }
    public void setPuntoOrdenDia(Integer puntoOrdenDia) { this.puntoOrdenDia = puntoOrdenDia; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTipoMayoriaRequerida() { return tipoMayoriaRequerida; }
    public void setTipoMayoriaRequerida(String tipoMayoriaRequerida) { this.tipoMayoriaRequerida = tipoMayoriaRequerida; }

    public OffsetDateTime getHoraApertura() { return horaApertura; }
    public void setHoraApertura(OffsetDateTime horaApertura) { this.horaApertura = horaApertura; }

    public OffsetDateTime getHoraCierre() { return horaCierre; }
    public void setHoraCierre(OffsetDateTime horaCierre) { this.horaCierre = horaCierre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getVotosSi() { return votosSi; }
    public void setVotosSi(BigDecimal votosSi) { this.votosSi = votosSi; }

    public BigDecimal getVotosNo() { return votosNo; }
    public void setVotosNo(BigDecimal votosNo) { this.votosNo = votosNo; }

    public BigDecimal getVotosBlanco() { return votosBlanco; }
    public void setVotosBlanco(BigDecimal votosBlanco) { this.votosBlanco = votosBlanco; }

    public BigDecimal getVotosAbstencion() { return votosAbstencion; }
    public void setVotosAbstencion(BigDecimal votosAbstencion) { this.votosAbstencion = votosAbstencion; }

    public Integer getTotalVotos() { return totalVotos; }
    public void setTotalVotos(Integer totalVotos) { this.totalVotos = totalVotos; }

    public Boolean getAprobada() { return aprobada; }
    public void setAprobada(Boolean aprobada) { this.aprobada = aprobada; }
}

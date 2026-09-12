package com.saed.backend.asambleas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AsambleaDTO {
    private Long idAsamblea;
    private Long idPropiedad;
    private String tipo;
    private String modalidad;
    private String titulo;
    private Integer convocatoriaNumero;
    private OffsetDateTime fechaHoraPrimeraConv;
    private OffsetDateTime fechaHoraSegundaConv;
    private String lugarOEnlace;
    private String ordenDelDia;
    private BigDecimal quorumRequeridoPct;
    private BigDecimal quorumAlcanzadoPct;
    private String estado;
    private Long convocadaPor;
    private OffsetDateTime fechaCreacion;
    private Integer totalAsistentes;
    private Integer totalVotaciones;

    public AsambleaDTO() {}

    public AsambleaDTO(Long idAsamblea, Long idPropiedad, String tipo, String modalidad, String titulo,
                       Integer convocatoriaNumero, OffsetDateTime fechaHoraPrimeraConv,
                       OffsetDateTime fechaHoraSegundaConv, String lugarOEnlace, String ordenDelDia,
                       BigDecimal quorumRequeridoPct, BigDecimal quorumAlcanzadoPct, String estado,
                       Long convocadaPor, OffsetDateTime fechaCreacion, Integer totalAsistentes,
                       Integer totalVotaciones) {
        this.idAsamblea = idAsamblea;
        this.idPropiedad = idPropiedad;
        this.tipo = tipo;
        this.modalidad = modalidad;
        this.titulo = titulo;
        this.convocatoriaNumero = convocatoriaNumero;
        this.fechaHoraPrimeraConv = fechaHoraPrimeraConv;
        this.fechaHoraSegundaConv = fechaHoraSegundaConv;
        this.lugarOEnlace = lugarOEnlace;
        this.ordenDelDia = ordenDelDia;
        this.quorumRequeridoPct = quorumRequeridoPct;
        this.quorumAlcanzadoPct = quorumAlcanzadoPct;
        this.estado = estado;
        this.convocadaPor = convocadaPor;
        this.fechaCreacion = fechaCreacion;
        this.totalAsistentes = totalAsistentes;
        this.totalVotaciones = totalVotaciones;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idAsamblea;
        private Long idPropiedad;
        private String tipo;
        private String modalidad;
        private String titulo;
        private Integer convocatoriaNumero;
        private OffsetDateTime fechaHoraPrimeraConv;
        private OffsetDateTime fechaHoraSegundaConv;
        private String lugarOEnlace;
        private String ordenDelDia;
        private BigDecimal quorumRequeridoPct;
        private BigDecimal quorumAlcanzadoPct;
        private String estado;
        private Long convocadaPor;
        private OffsetDateTime fechaCreacion;
        private Integer totalAsistentes;
        private Integer totalVotaciones;

        public Builder idAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; return this; }
        public Builder idPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; return this; }
        public Builder tipo(String tipo) { this.tipo = tipo; return this; }
        public Builder modalidad(String modalidad) { this.modalidad = modalidad; return this; }
        public Builder titulo(String titulo) { this.titulo = titulo; return this; }
        public Builder convocatoriaNumero(Integer convocatoriaNumero) { this.convocatoriaNumero = convocatoriaNumero; return this; }
        public Builder fechaHoraPrimeraConv(OffsetDateTime fechaHoraPrimeraConv) { this.fechaHoraPrimeraConv = fechaHoraPrimeraConv; return this; }
        public Builder fechaHoraSegundaConv(OffsetDateTime fechaHoraSegundaConv) { this.fechaHoraSegundaConv = fechaHoraSegundaConv; return this; }
        public Builder lugarOEnlace(String lugarOEnlace) { this.lugarOEnlace = lugarOEnlace; return this; }
        public Builder ordenDelDia(String ordenDelDia) { this.ordenDelDia = ordenDelDia; return this; }
        public Builder quorumRequeridoPct(BigDecimal quorumRequeridoPct) { this.quorumRequeridoPct = quorumRequeridoPct; return this; }
        public Builder quorumAlcanzadoPct(BigDecimal quorumAlcanzadoPct) { this.quorumAlcanzadoPct = quorumAlcanzadoPct; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder convocadaPor(Long convocadaPor) { this.convocadaPor = convocadaPor; return this; }
        public Builder fechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; return this; }
        public Builder totalAsistentes(Integer totalAsistentes) { this.totalAsistentes = totalAsistentes; return this; }
        public Builder totalVotaciones(Integer totalVotaciones) { this.totalVotaciones = totalVotaciones; return this; }

        public AsambleaDTO build() {
            return new AsambleaDTO(idAsamblea, idPropiedad, tipo, modalidad, titulo, convocatoriaNumero,
                    fechaHoraPrimeraConv, fechaHoraSegundaConv, lugarOEnlace, ordenDelDia,
                    quorumRequeridoPct, quorumAlcanzadoPct, estado, convocadaPor, fechaCreacion,
                    totalAsistentes, totalVotaciones);
        }
    }

    public Long getIdAsamblea() { return idAsamblea; }
    public void setIdAsamblea(Long idAsamblea) { this.idAsamblea = idAsamblea; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public Integer getConvocatoriaNumero() { return convocatoriaNumero; }
    public void setConvocatoriaNumero(Integer convocatoriaNumero) { this.convocatoriaNumero = convocatoriaNumero; }

    public OffsetDateTime getFechaHoraPrimeraConv() { return fechaHoraPrimeraConv; }
    public void setFechaHoraPrimeraConv(OffsetDateTime fechaHoraPrimeraConv) { this.fechaHoraPrimeraConv = fechaHoraPrimeraConv; }

    public OffsetDateTime getFechaHoraSegundaConv() { return fechaHoraSegundaConv; }
    public void setFechaHoraSegundaConv(OffsetDateTime fechaHoraSegundaConv) { this.fechaHoraSegundaConv = fechaHoraSegundaConv; }

    public String getLugarOEnlace() { return lugarOEnlace; }
    public void setLugarOEnlace(String lugarOEnlace) { this.lugarOEnlace = lugarOEnlace; }

    public String getOrdenDelDia() { return ordenDelDia; }
    public void setOrdenDelDia(String ordenDelDia) { this.ordenDelDia = ordenDelDia; }

    public BigDecimal getQuorumRequeridoPct() { return quorumRequeridoPct; }
    public void setQuorumRequeridoPct(BigDecimal quorumRequeridoPct) { this.quorumRequeridoPct = quorumRequeridoPct; }

    public BigDecimal getQuorumAlcanzadoPct() { return quorumAlcanzadoPct; }
    public void setQuorumAlcanzadoPct(BigDecimal quorumAlcanzadoPct) { this.quorumAlcanzadoPct = quorumAlcanzadoPct; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getConvocadaPor() { return convocadaPor; }
    public void setConvocadaPor(Long convocadaPor) { this.convocadaPor = convocadaPor; }

    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Integer getTotalAsistentes() { return totalAsistentes; }
    public void setTotalAsistentes(Integer totalAsistentes) { this.totalAsistentes = totalAsistentes; }

    public Integer getTotalVotaciones() { return totalVotaciones; }
    public void setTotalVotaciones(Integer totalVotaciones) { this.totalVotaciones = totalVotaciones; }
}

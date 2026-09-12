package com.saed.backend.asambleas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class AsambleaCreateRequestDTO {

    private Long idPropiedad;

    @NotBlank(message = "El tipo de asamblea es obligatorio")
    @Pattern(regexp = "ORDINARIA|EXTRAORDINARIA|SEGUNDA_CONVOCATORIA", message = "Tipo de asamblea inválido")
    private String tipo;

    @NotBlank(message = "La modalidad es obligatoria")
    @Pattern(regexp = "PRESENCIAL|VIRTUAL|MIXTA", message = "Modalidad inválida")
    private String modalidad;

    @NotBlank(message = "El título de la asamblea es obligatorio")
    @Size(max = 150, message = "El título no puede exceder 150 caracteres")
    private String titulo;

    private Integer convocatoriaNumero;

    @NotBlank(message = "La fecha y hora de primera convocatoria es obligatoria")
    private String fechaHoraPrimeraConv;

    private String fechaHoraSegundaConv;

    @NotBlank(message = "El lugar o enlace de la asamblea es obligatorio")
    @Size(max = 300, message = "El lugar o enlace no puede exceder 300 caracteres")
    private String lugarOEnlace;

    @NotBlank(message = "El orden del día es obligatorio")
    private String ordenDelDia;

    private BigDecimal quorumRequeridoPct;

    public AsambleaCreateRequestDTO() {}

    public AsambleaCreateRequestDTO(Long idPropiedad, String tipo, String modalidad, String titulo,
                                  Integer convocatoriaNumero, String fechaHoraPrimeraConv,
                                  String fechaHoraSegundaConv, String lugarOEnlace,
                                  String ordenDelDia, BigDecimal quorumRequeridoPct) {
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
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idPropiedad;
        private String tipo;
        private String modalidad;
        private String titulo;
        private Integer convocatoriaNumero;
        private String fechaHoraPrimeraConv;
        private String fechaHoraSegundaConv;
        private String lugarOEnlace;
        private String ordenDelDia;
        private BigDecimal quorumRequeridoPct;

        public Builder idPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; return this; }
        public Builder tipo(String tipo) { this.tipo = tipo; return this; }
        public Builder modalidad(String modalidad) { this.modalidad = modalidad; return this; }
        public Builder titulo(String titulo) { this.titulo = titulo; return this; }
        public Builder convocatoriaNumero(Integer convocatoriaNumero) { this.convocatoriaNumero = convocatoriaNumero; return this; }
        public Builder fechaHoraPrimeraConv(String fechaHoraPrimeraConv) { this.fechaHoraPrimeraConv = fechaHoraPrimeraConv; return this; }
        public Builder fechaHoraSegundaConv(String fechaHoraSegundaConv) { this.fechaHoraSegundaConv = fechaHoraSegundaConv; return this; }
        public Builder lugarOEnlace(String lugarOEnlace) { this.lugarOEnlace = lugarOEnlace; return this; }
        public Builder ordenDelDia(String ordenDelDia) { this.ordenDelDia = ordenDelDia; return this; }
        public Builder quorumRequeridoPct(BigDecimal quorumRequeridoPct) { this.quorumRequeridoPct = quorumRequeridoPct; return this; }

        public AsambleaCreateRequestDTO build() {
            return new AsambleaCreateRequestDTO(idPropiedad, tipo, modalidad, titulo, convocatoriaNumero,
                    fechaHoraPrimeraConv, fechaHoraSegundaConv, lugarOEnlace, ordenDelDia, quorumRequeridoPct);
        }
    }

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

    public String getFechaHoraPrimeraConv() { return fechaHoraPrimeraConv; }
    public void setFechaHoraPrimeraConv(String fechaHoraPrimeraConv) { this.fechaHoraPrimeraConv = fechaHoraPrimeraConv; }

    public String getFechaHoraSegundaConv() { return fechaHoraSegundaConv; }
    public void setFechaHoraSegundaConv(String fechaHoraSegundaConv) { this.fechaHoraSegundaConv = fechaHoraSegundaConv; }

    public String getLugarOEnlace() { return lugarOEnlace; }
    public void setLugarOEnlace(String lugarOEnlace) { this.lugarOEnlace = lugarOEnlace; }

    public String getOrdenDelDia() { return ordenDelDia; }
    public void setOrdenDelDia(String ordenDelDia) { this.ordenDelDia = ordenDelDia; }

    public BigDecimal getQuorumRequeridoPct() { return quorumRequeridoPct; }
    public void setQuorumRequeridoPct(BigDecimal quorumRequeridoPct) { this.quorumRequeridoPct = quorumRequeridoPct; }
}

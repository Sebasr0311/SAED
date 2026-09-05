package com.saed.backend.authorization.dto;

import java.math.BigDecimal;

public class UnitDTO {
    private Long id;
    private Long idPropiedad;
    private Long idBloque;
    private Long idTipoUnidad;
    private String identificador;
    private String tipoUnidadCodigo;
    private String tipoUnidadNombre;
    private String bloqueCodigo;
    private String bloqueNombre;
    private BigDecimal areaM2;
    private BigDecimal coeficienteCopropiedad;
    private String estado;

    public UnitDTO() {}

    public UnitDTO(Long id, String identificador) {
        this.id = id;
        this.identificador = identificador;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdApartamento() { return id; }
    public String getNumero() { return identificador; }
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }
    public Long getIdBloque() { return idBloque; }
    public void setIdBloque(Long idBloque) { this.idBloque = idBloque; }
    public Long getIdTipoUnidad() { return idTipoUnidad; }
    public void setIdTipoUnidad(Long idTipoUnidad) { this.idTipoUnidad = idTipoUnidad; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
    public String getTipoUnidadCodigo() { return tipoUnidadCodigo; }
    public void setTipoUnidadCodigo(String tipoUnidadCodigo) { this.tipoUnidadCodigo = tipoUnidadCodigo; }
    public String getTipoUnidadNombre() { return tipoUnidadNombre; }
    public void setTipoUnidadNombre(String tipoUnidadNombre) { this.tipoUnidadNombre = tipoUnidadNombre; }
    public String getBloqueCodigo() { return bloqueCodigo; }
    public void setBloqueCodigo(String bloqueCodigo) { this.bloqueCodigo = bloqueCodigo; }
    public String getBloqueNombre() { return bloqueNombre; }
    public void setBloqueNombre(String bloqueNombre) { this.bloqueNombre = bloqueNombre; }
    public BigDecimal getAreaM2() { return areaM2; }
    public void setAreaM2(BigDecimal areaM2) { this.areaM2 = areaM2; }
    public BigDecimal getCoeficienteCopropiedad() { return coeficienteCopropiedad; }
    public void setCoeficienteCopropiedad(BigDecimal coeficienteCopropiedad) { this.coeficienteCopropiedad = coeficienteCopropiedad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
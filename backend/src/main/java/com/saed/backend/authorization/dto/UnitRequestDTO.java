package com.saed.backend.authorization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class UnitRequestDTO {
    @NotNull private Long idPropiedad;
    @NotNull private Long idTipoUnidad;
    @NotBlank @Size(max = 30) private String identificador;
    private Long idBloque;
    private BigDecimal areaM2;
    private BigDecimal coeficienteCopropiedad;

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long v) { this.idPropiedad = v; }
    public Long getIdTipoUnidad() { return idTipoUnidad; }
    public void setIdTipoUnidad(Long v) { this.idTipoUnidad = v; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String v) { this.identificador = v; }
    public Long getIdBloque() { return idBloque; }
    public void setIdBloque(Long v) { this.idBloque = v; }
    public BigDecimal getAreaM2() { return areaM2; }
    public void setAreaM2(BigDecimal v) { this.areaM2 = v; }
    public BigDecimal getCoeficienteCopropiedad() { return coeficienteCopropiedad; }
    public void setCoeficienteCopropiedad(BigDecimal v) { this.coeficienteCopropiedad = v; }
}

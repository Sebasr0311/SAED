package com.saed.backend.reglamentos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReglamentoCreateRequestDTO {
    private Long idPropiedad;

    @NotBlank(message = "tipoNormativa is required")
    @Size(max = 50, message = "tipoNormativa cannot exceed 50 characters")
    private String tipoNormativa;

    @NotBlank(message = "titulo is required")
    @Size(max = 255, message = "titulo cannot exceed 255 characters")
    private String titulo;

    @Size(max = 1000, message = "descripcion cannot exceed 1000 characters")
    private String descripcion;

    @NotNull(message = "idDocumento is required")
    private Long idDocumento;

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getTipoNormativa() { return tipoNormativa; }
    public void setTipoNormativa(String tipoNormativa) { this.tipoNormativa = tipoNormativa; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Long getIdDocumento() { return idDocumento; }
    public void setIdDocumento(Long idDocumento) { this.idDocumento = idDocumento; }
}

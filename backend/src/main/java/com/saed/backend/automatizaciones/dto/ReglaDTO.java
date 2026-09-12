package com.saed.backend.automatizaciones.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class ReglaDTO {
    private Long idRegla;
    private Long idOrganizacion;
    private Long idPropiedad;
    private String nombrePropiedad;
    private Long idEvento;
    private String codigoEvento;
    private String nombreEvento;
    private String moduloOrigen;
    private String variablesPayload;
    private String nombre;
    private String descripcion;
    private String condicionJson;
    private String estado;
    private OffsetDateTime fechaCreacion;
    private Long creadoPor;
    private String creadorNombre;
    private List<AccionDTO> acciones;

    public ReglaDTO() {}

    public ReglaDTO(Long idRegla, Long idOrganizacion, Long idPropiedad, String nombrePropiedad,
                    Long idEvento, String codigoEvento, String nombreEvento, String moduloOrigen,
                    String variablesPayload, String nombre, String descripcion, String condicionJson,
                    String estado, OffsetDateTime fechaCreacion, Long creadoPor, String creadorNombre,
                    List<AccionDTO> acciones) {
        this.idRegla = idRegla;
        this.idOrganizacion = idOrganizacion;
        this.idPropiedad = idPropiedad;
        this.nombrePropiedad = nombrePropiedad;
        this.idEvento = idEvento;
        this.codigoEvento = codigoEvento;
        this.nombreEvento = nombreEvento;
        this.moduloOrigen = moduloOrigen;
        this.variablesPayload = variablesPayload;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.condicionJson = condicionJson;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.creadoPor = creadoPor;
        this.creadorNombre = creadorNombre;
        this.acciones = acciones;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idRegla;
        private Long idOrganizacion;
        private Long idPropiedad;
        private String nombrePropiedad;
        private Long idEvento;
        private String codigoEvento;
        private String nombreEvento;
        private String moduloOrigen;
        private String variablesPayload;
        private String nombre;
        private String descripcion;
        private String condicionJson;
        private String estado;
        private OffsetDateTime fechaCreacion;
        private Long creadoPor;
        private String creadorNombre;
        private List<AccionDTO> acciones;

        public Builder idRegla(Long idRegla) { this.idRegla = idRegla; return this; }
        public Builder idOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; return this; }
        public Builder idPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; return this; }
        public Builder nombrePropiedad(String nombrePropiedad) { this.nombrePropiedad = nombrePropiedad; return this; }
        public Builder idEvento(Long idEvento) { this.idEvento = idEvento; return this; }
        public Builder codigoEvento(String codigoEvento) { this.codigoEvento = codigoEvento; return this; }
        public Builder nombreEvento(String nombreEvento) { this.nombreEvento = nombreEvento; return this; }
        public Builder moduloOrigen(String moduloOrigen) { this.moduloOrigen = moduloOrigen; return this; }
        public Builder variablesPayload(String variablesPayload) { this.variablesPayload = variablesPayload; return this; }
        public Builder nombre(String nombre) { this.nombre = nombre; return this; }
        public Builder descripcion(String descripcion) { this.descripcion = descripcion; return this; }
        public Builder condicionJson(String condicionJson) { this.condicionJson = condicionJson; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder fechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; return this; }
        public Builder creadoPor(Long creadoPor) { this.creadoPor = creadoPor; return this; }
        public Builder creadorNombre(String creadorNombre) { this.creadorNombre = creadorNombre; return this; }
        public Builder acciones(List<AccionDTO> acciones) { this.acciones = acciones; return this; }

        public ReglaDTO build() {
            return new ReglaDTO(idRegla, idOrganizacion, idPropiedad, nombrePropiedad, idEvento,
                    codigoEvento, nombreEvento, moduloOrigen, variablesPayload, nombre, descripcion,
                    condicionJson, estado, fechaCreacion, creadoPor, creadorNombre, acciones);
        }
    }

    public Long getIdRegla() { return idRegla; }
    public void setIdRegla(Long idRegla) { this.idRegla = idRegla; }

    public Long getIdOrganizacion() { return idOrganizacion; }
    public void setIdOrganizacion(Long idOrganizacion) { this.idOrganizacion = idOrganizacion; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public String getNombrePropiedad() { return nombrePropiedad; }
    public void setNombrePropiedad(String nombrePropiedad) { this.nombrePropiedad = nombrePropiedad; }

    public Long getIdEvento() { return idEvento; }
    public void setIdEvento(Long idEvento) { this.idEvento = idEvento; }

    public String getCodigoEvento() { return codigoEvento; }
    public void setCodigoEvento(String codigoEvento) { this.codigoEvento = codigoEvento; }

    public String getNombreEvento() { return nombreEvento; }
    public void setNombreEvento(String nombreEvento) { this.nombreEvento = nombreEvento; }

    public String getModuloOrigen() { return moduloOrigen; }
    public void setModuloOrigen(String moduloOrigen) { this.moduloOrigen = moduloOrigen; }

    public String getVariablesPayload() { return variablesPayload; }
    public void setVariablesPayload(String variablesPayload) { this.variablesPayload = variablesPayload; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCondicionJson() { return condicionJson; }
    public void setCondicionJson(String condicionJson) { this.condicionJson = condicionJson; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Long getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Long creadoPor) { this.creadoPor = creadoPor; }

    public String getCreadorNombre() { return creadorNombre; }
    public void setCreadorNombre(String creadorNombre) { this.creadorNombre = creadorNombre; }

    public List<AccionDTO> getAcciones() { return acciones; }
    public void setAcciones(List<AccionDTO> acciones) { this.acciones = acciones; }
}

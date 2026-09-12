package com.saed.backend.automatizaciones.dto;

public class EventoDTO {
    private Long idEvento;
    private String codigo;
    private String nombre;
    private String moduloOrigen;
    private String descripcion;
    private String variablesPayload;

    public EventoDTO() {}

    public EventoDTO(Long idEvento, String codigo, String nombre, String moduloOrigen, String descripcion, String variablesPayload) {
        this.idEvento = idEvento;
        this.codigo = codigo;
        this.nombre = nombre;
        this.moduloOrigen = moduloOrigen;
        this.descripcion = descripcion;
        this.variablesPayload = variablesPayload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long idEvento;
        private String codigo;
        private String nombre;
        private String moduloOrigen;
        private String descripcion;
        private String variablesPayload;

        public Builder idEvento(Long idEvento) { this.idEvento = idEvento; return this; }
        public Builder codigo(String codigo) { this.codigo = codigo; return this; }
        public Builder nombre(String nombre) { this.nombre = nombre; return this; }
        public Builder moduloOrigen(String moduloOrigen) { this.moduloOrigen = moduloOrigen; return this; }
        public Builder descripcion(String descripcion) { this.descripcion = descripcion; return this; }
        public Builder variablesPayload(String variablesPayload) { this.variablesPayload = variablesPayload; return this; }

        public EventoDTO build() {
            return new EventoDTO(idEvento, codigo, nombre, moduloOrigen, descripcion, variablesPayload);
        }
    }

    public Long getIdEvento() { return idEvento; }
    public void setIdEvento(Long idEvento) { this.idEvento = idEvento; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getModuloOrigen() { return moduloOrigen; }
    public void setModuloOrigen(String moduloOrigen) { this.moduloOrigen = moduloOrigen; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getVariablesPayload() { return variablesPayload; }
    public void setVariablesPayload(String variablesPayload) { this.variablesPayload = variablesPayload; }
}

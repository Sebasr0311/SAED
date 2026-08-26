package com.saed.backend.identity.dto;

public class AuthData {
    private Long idUsuario;
    private String hashPassword;
    private String estado;
    private Integer intentosFallidos;

    public AuthData() {}

    public AuthData(Long idUsuario, String hashPassword, String estado, Integer intentosFallidos) {
        this.idUsuario = idUsuario;
        this.hashPassword = hashPassword;
        this.estado = estado;
        this.intentosFallidos = intentosFallidos;
    }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public String getHashPassword() { return hashPassword; }
    public void setHashPassword(String hashPassword) { this.hashPassword = hashPassword; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Integer getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(Integer intentosFallidos) { this.intentosFallidos = intentosFallidos; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long idUsuario;
        private String hashPassword;
        private String estado;
        private Integer intentosFallidos;

        public Builder idUsuario(Long idUsuario) { this.idUsuario = idUsuario; return this; }
        public Builder hashPassword(String hashPassword) { this.hashPassword = hashPassword; return this; }
        public Builder estado(String estado) { this.estado = estado; return this; }
        public Builder intentosFallidos(Integer intentosFallidos) { this.intentosFallidos = intentosFallidos; return this; }
        public AuthData build() { return new AuthData(idUsuario, hashPassword, estado, intentosFallidos); }
    }
}

package com.saed.backend.identity.dto;

public class AuthResponse {
    private String token;
    private boolean requiresPasswordChange;
    private Long idUsuario;
    private AuthUserDTO usuario;

    public AuthResponse(String token, boolean requiresPasswordChange, Long idUsuario) {
        this.token = token;
        this.requiresPasswordChange = requiresPasswordChange;
        this.idUsuario = idUsuario;
    }

    public AuthResponse(String token, boolean requiresPasswordChange, Long idUsuario, AuthUserDTO usuario) {
        this.token = token;
        this.requiresPasswordChange = requiresPasswordChange;
        this.idUsuario = idUsuario;
        this.usuario = usuario;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public boolean isRequiresPasswordChange() { return requiresPasswordChange; }
    public void setRequiresPasswordChange(boolean requiresPasswordChange) { this.requiresPasswordChange = requiresPasswordChange; }
    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public AuthUserDTO getUsuario() { return usuario; }
    public void setUsuario(AuthUserDTO usuario) { this.usuario = usuario; }
}
package com.edificio.admin.rest.dto;

import java.util.Map;

public class LoginResponse {
    private String token;
    private Map<String, Object> usuario;

    public LoginResponse() {}
    public LoginResponse(String token, Map<String, Object> usuario) {
        this.token = token;
        this.usuario = usuario;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Map<String, Object> getUsuario() { return usuario; }
    public void setUsuario(Map<String, Object> usuario) { this.usuario = usuario; }
}

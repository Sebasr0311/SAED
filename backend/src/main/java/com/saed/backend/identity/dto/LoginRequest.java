package com.saed.backend.identity.dto;

public class LoginRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) {
        if (this.username == null || this.username.isBlank()) {
            this.username = email;
        }
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
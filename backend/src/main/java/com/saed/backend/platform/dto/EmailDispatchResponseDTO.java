package com.saed.backend.platform.dto;

public class EmailDispatchResponseDTO {
    private String estado; // ENVIADO, SIMULADO, FALLIDO
    private String detalle;
    private boolean real;
    private String emailDestino;
    private String username;
    private String passwordGenerada;
    private String fecha;

    public EmailDispatchResponseDTO() {}

    public EmailDispatchResponseDTO(String estado, String detalle, boolean real, String emailDestino, String username, String passwordGenerada, String fecha) {
        this.estado = estado;
        this.detalle = detalle;
        this.real = real;
        this.emailDestino = emailDestino;
        this.username = username;
        this.passwordGenerada = passwordGenerada;
        this.fecha = fecha;
    }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public boolean isReal() { return real; }
    public void setReal(boolean real) { this.real = real; }

    public String getEmailDestino() { return emailDestino; }
    public void setEmailDestino(String emailDestino) { this.emailDestino = emailDestino; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordGenerada() { return passwordGenerada; }
    public void setPasswordGenerada(String passwordGenerada) { this.passwordGenerada = passwordGenerada; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}

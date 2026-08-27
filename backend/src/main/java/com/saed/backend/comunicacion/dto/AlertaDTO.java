package com.saed.backend.comunicacion.dto;
import java.time.ZonedDateTime;
public class AlertaDTO {
    private Long idAlerta;
    private Long idPropiedad;
    private String tipoAlerta;
    private String numeroApartamento;
    private String nombreResidente;
    private String estadoCuota;
    private String leida;
    private ZonedDateTime fechaCreacion;
    
    public AlertaDTO() {}
    public AlertaDTO(Long idAlerta, Long idPropiedad, String tipoAlerta, String numeroApartamento, String nombreResidente, String estadoCuota, String leida, ZonedDateTime fechaCreacion) {
        this.idAlerta = idAlerta; this.idPropiedad = idPropiedad; this.tipoAlerta = tipoAlerta; this.numeroApartamento = numeroApartamento; this.nombreResidente = nombreResidente; this.estadoCuota = estadoCuota; this.leida = leida; this.fechaCreacion = fechaCreacion;
    }
    public Long getIdAlerta() { return idAlerta; }
    public void setIdAlerta(Long idAlerta) { this.idAlerta = idAlerta; }
    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }
    public String getTipoAlerta() { return tipoAlerta; }
    public void setTipoAlerta(String tipoAlerta) { this.tipoAlerta = tipoAlerta; }
    public String getNumeroApartamento() { return numeroApartamento; }
    public void setNumeroApartamento(String numeroApartamento) { this.numeroApartamento = numeroApartamento; }
    public String getNombreResidente() { return nombreResidente; }
    public void setNombreResidente(String nombreResidente) { this.nombreResidente = nombreResidente; }
    public String getEstadoCuota() { return estadoCuota; }
    public void setEstadoCuota(String estadoCuota) { this.estadoCuota = estadoCuota; }
    public String getLeida() { return leida; }
    public void setLeida(String leida) { this.leida = leida; }
    public ZonedDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(ZonedDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}

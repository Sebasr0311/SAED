package com.edificio.admin.rest.dto;

import com.edificio.admin.model.enums.TipoContrato;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ContratoDetalleDTO {

    private Integer idContrato;
    private String numeroContrato;
    private LocalDate fechaGeneracion;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String periodoVigencia;
    private TipoContrato tipoContrato;
    private String estado;
    private String numeroApartamento;
    private String tipoApartamento;
    private Integer piso;
    private BigDecimal area;
    private Integer capacidadMaxima;
    private String nombreCompletoResidente;
    private String nombresResidente;
    private String apellidosResidente;
    private String tipoDocumentoResidente;
    private String numeroDocumentoResidente;
    private String telefonoResidente;
    private String correoResidente;
    private String nombreTutor;
    private String cedulaTutor;
    private String relacionTutor;
    private String nombreParqueadero;
    private String nombreEdificio;
    private String direccionEdificio;
    private String ciudadEdificio;
    private String nitAdministrador;
    private BigDecimal valorCanon;
    private BigDecimal valorAdministracion;
    private BigDecimal valorTotal;
    private BigDecimal valorDeposito;
    private int diaPago;
    private int diasGracia;
    private BigDecimal porcentajeMora;
    private Integer diasInspeccion;
    private Integer diasAvisoPrevio;
    private BigDecimal penalizacionSalidaAnticipada;
    private String textoRenovacion;
    private String telefonoAdministracion;
    private String correoAdministracion;
    private String numeroContratoAnterior;
    private String historialContratos;

    public Integer getIdContrato() { return idContrato; }
    public void setIdContrato(Integer v) { this.idContrato = v; }

    public String getNumeroContrato() { return numeroContrato; }
    public void setNumeroContrato(String v) { this.numeroContrato = v; }

    public LocalDate getFechaGeneracion() { return fechaGeneracion; }
    public void setFechaGeneracion(LocalDate v) { this.fechaGeneracion = v; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate v) { this.fechaInicio = v; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate v) { this.fechaFin = v; }

    public String getPeriodoVigencia() { return periodoVigencia; }
    public void setPeriodoVigencia(String v) { this.periodoVigencia = v; }

    public TipoContrato getTipoContrato() { return tipoContrato; }
    public void setTipoContrato(TipoContrato v) { this.tipoContrato = v; }

    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }

    public String getNumeroApartamento() { return numeroApartamento; }
    public void setNumeroApartamento(String v) { this.numeroApartamento = v; }

    public String getTipoApartamento() { return tipoApartamento; }
    public void setTipoApartamento(String v) { this.tipoApartamento = v; }

    public Integer getPiso() { return piso; }
    public void setPiso(Integer v) { this.piso = v; }

    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal v) { this.area = v; }

    public Integer getCapacidadMaxima() { return capacidadMaxima; }
    public void setCapacidadMaxima(Integer v) { this.capacidadMaxima = v; }

    public String getNombreCompletoResidente() { return nombreCompletoResidente; }
    public void setNombreCompletoResidente(String v) { this.nombreCompletoResidente = v; }

    public String getNombresResidente() { return nombresResidente; }
    public void setNombresResidente(String v) { this.nombresResidente = v; }

    public String getApellidosResidente() { return apellidosResidente; }
    public void setApellidosResidente(String v) { this.apellidosResidente = v; }

    public String getTipoDocumentoResidente() { return tipoDocumentoResidente; }
    public void setTipoDocumentoResidente(String v) { this.tipoDocumentoResidente = v; }

    public String getNumeroDocumentoResidente() { return numeroDocumentoResidente; }
    public void setNumeroDocumentoResidente(String v) { this.numeroDocumentoResidente = v; }

    public String getTelefonoResidente() { return telefonoResidente; }
    public void setTelefonoResidente(String v) { this.telefonoResidente = v; }

    public String getCorreoResidente() { return correoResidente; }
    public void setCorreoResidente(String v) { this.correoResidente = v; }

    public String getNombreTutor() { return nombreTutor; }
    public void setNombreTutor(String v) { this.nombreTutor = v; }

    public String getCedulaTutor() { return cedulaTutor; }
    public void setCedulaTutor(String v) { this.cedulaTutor = v; }

    public String getRelacionTutor() { return relacionTutor; }
    public void setRelacionTutor(String v) { this.relacionTutor = v; }

    public String getNombreParqueadero() { return nombreParqueadero; }
    public void setNombreParqueadero(String v) { this.nombreParqueadero = v; }

    public String getNombreEdificio() { return nombreEdificio; }
    public void setNombreEdificio(String v) { this.nombreEdificio = v; }

    public String getDireccionEdificio() { return direccionEdificio; }
    public void setDireccionEdificio(String v) { this.direccionEdificio = v; }

    public String getCiudadEdificio() { return ciudadEdificio; }
    public void setCiudadEdificio(String v) { this.ciudadEdificio = v; }

    public String getNitAdministrador() { return nitAdministrador; }
    public void setNitAdministrador(String v) { this.nitAdministrador = v; }

    public BigDecimal getValorCanon() { return valorCanon; }
    public void setValorCanon(BigDecimal v) { this.valorCanon = v; }

    public BigDecimal getValorAdministracion() { return valorAdministracion; }
    public void setValorAdministracion(BigDecimal v) { this.valorAdministracion = v; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal v) { this.valorTotal = v; }

    public BigDecimal getValorDeposito() { return valorDeposito; }
    public void setValorDeposito(BigDecimal v) { this.valorDeposito = v; }

    public int getDiaPago() { return diaPago; }
    public void setDiaPago(int v) { this.diaPago = v; }

    public int getDiasGracia() { return diasGracia; }
    public void setDiasGracia(int v) { this.diasGracia = v; }

    public BigDecimal getPorcentajeMora() { return porcentajeMora; }
    public void setPorcentajeMora(BigDecimal v) { this.porcentajeMora = v; }

    public Integer getDiasInspeccion() { return diasInspeccion; }
    public void setDiasInspeccion(Integer v) { this.diasInspeccion = v; }

    public Integer getDiasAvisoPrevio() { return diasAvisoPrevio; }
    public void setDiasAvisoPrevio(Integer v) { this.diasAvisoPrevio = v; }

    public BigDecimal getPenalizacionSalidaAnticipada() { return penalizacionSalidaAnticipada; }
    public void setPenalizacionSalidaAnticipada(BigDecimal v) { this.penalizacionSalidaAnticipada = v; }

    public String getTextoRenovacion() { return textoRenovacion; }
    public void setTextoRenovacion(String v) { this.textoRenovacion = v; }

    public String getTelefonoAdministracion() { return telefonoAdministracion; }
    public void setTelefonoAdministracion(String v) { this.telefonoAdministracion = v; }

    public String getCorreoAdministracion() { return correoAdministracion; }
    public void setCorreoAdministracion(String v) { this.correoAdministracion = v; }

    public String getNumeroContratoAnterior() { return numeroContratoAnterior; }
    public void setNumeroContratoAnterior(String v) { this.numeroContratoAnterior = v; }

    public String getHistorialContratos() { return historialContratos; }
    public void setHistorialContratos(String v) { this.historialContratos = v; }
}

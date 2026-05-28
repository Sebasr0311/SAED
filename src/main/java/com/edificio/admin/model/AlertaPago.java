package com.edificio.admin.model;

import java.time.LocalDateTime;

/**
 * Alerta de pago generada por Oracle (trigger TRG_ALERTAS_PAGO).
 * Corresponde a la tabla ALERTAS_PAGO.
 *
 * Las alertas son de solo lectura desde la app (Oracle las crea);
 * la única operación de escritura es marcarLeida().
 *
 * Campos transient (calculados con JOIN en DAO):
 *   - nombreResidente, numeroApartamento, anio, mes, estadoCuota
 */
public class AlertaPago {

    public enum TipoAlerta { PROXIMO_VENCIMIENTO, VENCIDA, EN_MORA }
    public enum Canal      { SISTEMA, EMAIL, SMS, WHATSAPP }

    private Integer       idAlerta;
    private Integer       idCuota;
    private TipoAlerta    tipoAlerta;
    private Canal         canal;
    private boolean       leida;
    private LocalDateTime enviadaEn;
    private LocalDateTime leidaEn;

    // Campos transient — calculados con JOIN en AlertaPagoDAO.findAll()
    private String  nombreResidente;
    private String  numeroApartamento;
    private Integer anio;
    private Integer mes;
    private String  estadoCuota;

    public AlertaPago() {}

    // ── getters / setters ────────────────────────────────────────────────────

    public Integer    getIdAlerta()                       { return idAlerta; }
    public void       setIdAlerta(Integer v)              { this.idAlerta = v; }

    public Integer    getIdCuota()                        { return idCuota; }
    public void       setIdCuota(Integer v)               { this.idCuota = v; }

    public TipoAlerta getTipoAlerta()                     { return tipoAlerta; }
    public void       setTipoAlerta(TipoAlerta v)         { this.tipoAlerta = v; }

    public Canal      getCanal()                          { return canal; }
    public void       setCanal(Canal v)                   { this.canal = v; }

    public boolean    isLeida()                           { return leida; }
    public void       setLeida(boolean v)                 { this.leida = v; }

    public LocalDateTime getEnviadaEn()                   { return enviadaEn; }
    public void          setEnviadaEn(LocalDateTime v)    { this.enviadaEn = v; }

    public LocalDateTime getLeidaEn()                     { return leidaEn; }
    public void          setLeidaEn(LocalDateTime v)      { this.leidaEn = v; }

    // Transient
    public String  getNombreResidente()                   { return nombreResidente; }
    public void    setNombreResidente(String v)           { this.nombreResidente = v; }

    public String  getNumeroApartamento()                 { return numeroApartamento; }
    public void    setNumeroApartamento(String v)         { this.numeroApartamento = v; }

    public Integer getAnio()                              { return anio; }
    public void    setAnio(Integer v)                     { this.anio = v; }

    public Integer getMes()                               { return mes; }
    public void    setMes(Integer v)                      { this.mes = v; }

    public String  getEstadoCuota()                       { return estadoCuota; }
    public void    setEstadoCuota(String v)               { this.estadoCuota = v; }

    /** Periodo formateado "AAAA-MM" para mostrar en tabla. */
    public String getPeriodo() {
        if (anio == null || mes == null) return "-";
        return String.format("%d-%02d", anio, mes);
    }
}

package com.edificio.admin.model;

import java.time.LocalDateTime;

/**
 * Codigo QR de un solo uso asociado a una visita.
 * Corresponde a la tabla QR_ACCESOS.
 * codigoQr = LOWER(RAWTOHEX(SYS_GUID())) generado en Oracle, 32 chars hex.
 */
public class QRAcceso {

    private Integer       idQr;
    private Integer       idVisita;
    private String        codigoQr;
    private LocalDateTime fechaExpiracion;
    private boolean       usado;
    private LocalDateTime fechaUso;        // nullable
    private Integer       idVigilanteUso;  // nullable

    public QRAcceso() {}

    // ---- getters / setters ----

    public Integer       getIdQr()                              { return idQr; }
    public void          setIdQr(Integer v)                     { this.idQr = v; }

    public Integer       getIdVisita()                          { return idVisita; }
    public void          setIdVisita(Integer v)                 { this.idVisita = v; }

    public String        getCodigoQr()                         { return codigoQr; }
    public void          setCodigoQr(String v)                  { this.codigoQr = v; }

    public LocalDateTime getFechaExpiracion()                   { return fechaExpiracion; }
    public void          setFechaExpiracion(LocalDateTime v)    { this.fechaExpiracion = v; }

    public boolean       isUsado()                              { return usado; }
    public void          setUsado(boolean v)                    { this.usado = v; }

    public LocalDateTime getFechaUso()                          { return fechaUso; }
    public void          setFechaUso(LocalDateTime v)           { this.fechaUso = v; }

    public Integer       getIdVigilanteUso()                    { return idVigilanteUso; }
    public void          setIdVigilanteUso(Integer v)           { this.idVigilanteUso = v; }
}

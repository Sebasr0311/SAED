package com.edificio.admin.model;

import com.edificio.admin.model.enums.EstadoApartamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unidad habitacional del edificio.
 * Corresponde a la tabla APARTAMENTOS.
 */
public class Apartamento {

    private Integer           idApartamento;
    private String            numero;
    private Integer           piso;
    /** ESTUDIO / 1HAB / 2HAB / 3HAB / PENTHOUSE */
    private String            tipo;
    private BigDecimal        areaM2;
    private Integer           capacidadMaxima;
    private BigDecimal        administracion;
    private EstadoApartamento estado;
    private boolean           activo;
    private LocalDateTime     creadoEn;
    private LocalDateTime     actualizadoEn;

    public Apartamento() {}

    // ---- getters / setters ----

    public Integer           getIdApartamento()                      { return idApartamento; }
    public void              setIdApartamento(Integer v)             { this.idApartamento = v; }

    public String            getNumero()                             { return numero; }
    public void              setNumero(String v)                     { this.numero = v; }

    public Integer           getPiso()                               { return piso; }
    public void              setPiso(Integer v)                      { this.piso = v; }

    public String            getTipo()                               { return tipo; }
    public void              setTipo(String v)                       { this.tipo = v; }

    public BigDecimal        getAreaM2()                             { return areaM2; }
    public void              setAreaM2(BigDecimal v)                 { this.areaM2 = v; }

    public Integer           getCapacidadMaxima()                    { return capacidadMaxima; }
    public void              setCapacidadMaxima(Integer v)           { this.capacidadMaxima = v; }

    public BigDecimal        getAdministracion()                     { return administracion; }
    public void              setAdministracion(BigDecimal v)          { this.administracion = v; }

    public EstadoApartamento getEstado()                             { return estado; }
    public void              setEstado(EstadoApartamento v)          { this.estado = v; }

    public boolean           isActivo()                              { return activo; }
    public void              setActivo(boolean v)                    { this.activo = v; }

    public LocalDateTime     getCreadoEn()                           { return creadoEn; }
    public void              setCreadoEn(LocalDateTime v)            { this.creadoEn = v; }

    public LocalDateTime     getActualizadoEn()                      { return actualizadoEn; }
    public void              setActualizadoEn(LocalDateTime v)       { this.actualizadoEn = v; }

    @Override
    public String toString() { return numero + " (Piso " + piso + ")"; }
}

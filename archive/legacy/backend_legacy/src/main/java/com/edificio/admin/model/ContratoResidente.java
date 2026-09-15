package com.edificio.admin.model;

/**
 * Tabla de union N:M entre CONTRATOS y RESIDENTES.
 * Define el rol de cada residente en el contrato.
 * Corresponde a la tabla CONTRATO_RESIDENTE.
 * Valores validos de rolEnContrato: ARRENDATARIO / CODEUDOR / RESIDENTE_MENOR / OTRO
 */
public class ContratoResidente {

    private Integer idContratoRes;
    private Integer idContrato;
    private Integer idResidente;
    /** ARRENDATARIO / CODEUDOR / RESIDENTE_MENOR / OTRO */
    private String  rolEnContrato;

    public ContratoResidente() {}

    public ContratoResidente(Integer idContratoRes, Integer idContrato,
                              Integer idResidente, String rolEnContrato) {
        this.idContratoRes = idContratoRes;
        this.idContrato    = idContrato;
        this.idResidente   = idResidente;
        this.rolEnContrato = rolEnContrato;
    }

    // ---- getters / setters ----

    public Integer getIdContratoRes()                   { return idContratoRes; }
    public void    setIdContratoRes(Integer v)          { this.idContratoRes = v; }

    public Integer getIdContrato()                      { return idContrato; }
    public void    setIdContrato(Integer v)             { this.idContrato = v; }

    public Integer getIdResidente()                     { return idResidente; }
    public void    setIdResidente(Integer v)            { this.idResidente = v; }

    public String  getRolEnContrato()                   { return rolEnContrato; }
    public void    setRolEnContrato(String v)           { this.rolEnContrato = v; }

    @Override
    public String toString() {
        return "Contrato #" + idContrato + " (" + rolEnContrato + ")";
    }
}

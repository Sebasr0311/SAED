package com.edificio.admin.model;

/**
 * Catálogo de tipos de documento de identidad.
 * Corresponde a la tabla TIPOS_DOCUMENTO.
 */
public class TipoDocumento {

    private Integer idTipoDoc;
    private String  codigo;       // CC, TI, CE, PP, PEP, RC, NIT
    private String  descripcion;
    private boolean activo;

    public TipoDocumento() {}

    public TipoDocumento(Integer idTipoDoc, String codigo, String descripcion, boolean activo) {
        this.idTipoDoc   = idTipoDoc;
        this.codigo      = codigo;
        this.descripcion = descripcion;
        this.activo      = activo;
    }

    // ---- getters / setters ----

    public Integer getIdTipoDoc()                   { return idTipoDoc; }
    public void    setIdTipoDoc(Integer v)           { this.idTipoDoc = v; }

    public String  getCodigo()                      { return codigo; }
    public void    setCodigo(String v)               { this.codigo = v; }

    public String  getDescripcion()                 { return descripcion; }
    public void    setDescripcion(String v)          { this.descripcion = v; }

    public boolean isActivo()                       { return activo; }
    public void    setActivo(boolean v)              { this.activo = v; }

    /** Etiqueta legible para ComboBox: "descripcion (codigo)". */
    @Override
    public String toString() {
        return descripcion + " (" + codigo + ")";
    }
}

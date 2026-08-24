package com.edificio.admin.model;

/**
 * Clase base abstracta para entidades con datos personales.
 * La heredan Residente y Visitante.
 * El campo idTipoDoc referencia TIPOS_DOCUMENTO.id_tipo_doc.
 */
public abstract class Persona {

    private Integer id;
    private Integer idTipoDoc;
    private String  numeroDocumento;
    private String  nombres;
    private String  apellidos;
    private String  telefono;

    protected Persona() {}

    protected Persona(Integer id, Integer idTipoDoc, String numeroDocumento,
                      String nombres, String apellidos, String telefono) {
        this.id              = id;
        this.idTipoDoc       = idTipoDoc;
        this.numeroDocumento = numeroDocumento;
        this.nombres         = nombres;
        this.apellidos       = apellidos;
        this.telefono        = telefono;
    }

    // ---- getters / setters ----

    public Integer getId()                        { return id; }
    public void    setId(Integer id)              { this.id = id; }

    public Integer getIdTipoDoc()                 { return idTipoDoc; }
    public void    setIdTipoDoc(Integer v)        { this.idTipoDoc = v; }

    public String  getNumeroDocumento()           { return numeroDocumento; }
    public void    setNumeroDocumento(String v)   { this.numeroDocumento = v; }

    public String  getNombres()                   { return nombres; }
    public void    setNombres(String v)           { this.nombres = v; }

    public String  getApellidos()                 { return apellidos; }
    public void    setApellidos(String v)         { this.apellidos = v; }

    public String  getTelefono()                  { return telefono; }
    public void    setTelefono(String v)          { this.telefono = v; }

    /** Nombre completo para mostrar en la UI. */
    public String getNombreCompleto() {
        return (nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "");
    }
}

package com.edificio.admin.model;

import java.time.LocalDateTime;

/**
 * Tutor o responsable de un residente menor de edad.
 * Corresponde a la tabla TUTORES.
 *
 * Restricciones en BD:
 *   - id_residente_menor FK -> RESIDENTES (CASCADE DELETE)
 *   - UQ_TUTOR_DOC (id_tipo_doc, numero_documento) — el tutor no puede tener dos registros con el mismo doc
 *   - CHK_TUTOR_PARENTESCO: parentesco IN ('PADRE','MADRE','ABUELO','ABUELA','TIO','TIA',
 *                                           'HERMANO','HERMANA','TUTOR_LEGAL','OTRO')
 */
public class Tutor {

    public enum Parentesco {
        PADRE, MADRE, ABUELO, ABUELA, TIO, TIA,
        HERMANO, HERMANA, TUTOR_LEGAL, OTRO
    }

    private Integer       idTutor;
    private Integer       idResidenteMenor;   // FK -> RESIDENTES
    private Integer       idTipoDoc;
    private String        numeroDocumento;
    private String        nombres;
    private String        apellidos;
    private String        telefono;
    private String        email;
    private Parentesco    parentesco;
    private String        docPdfUrl;           // ruta/URL al documento escaneado (opcional)
    private LocalDateTime fechaRegistro;
    private LocalDateTime actualizadoEn;

    public Tutor() {}

    // ── getters / setters ────────────────────────────────────────────────────

    public Integer    getIdTutor()                       { return idTutor; }
    public void       setIdTutor(Integer v)              { this.idTutor = v; }

    public Integer    getIdResidenteMenor()              { return idResidenteMenor; }
    public void       setIdResidenteMenor(Integer v)     { this.idResidenteMenor = v; }

    public Integer    getIdTipoDoc()                     { return idTipoDoc; }
    public void       setIdTipoDoc(Integer v)            { this.idTipoDoc = v; }

    public String     getNumeroDocumento()               { return numeroDocumento; }
    public void       setNumeroDocumento(String v)       { this.numeroDocumento = v; }

    public String     getNombres()                       { return nombres; }
    public void       setNombres(String v)               { this.nombres = v; }

    public String     getApellidos()                     { return apellidos; }
    public void       setApellidos(String v)             { this.apellidos = v; }

    public String     getTelefono()                      { return telefono; }
    public void       setTelefono(String v)              { this.telefono = v; }

    public String     getEmail()                         { return email; }
    public void       setEmail(String v)                 { this.email = v; }

    public Parentesco getParentesco()                    { return parentesco; }
    public void       setParentesco(Parentesco v)        { this.parentesco = v; }

    public String     getDocPdfUrl()                     { return docPdfUrl; }
    public void       setDocPdfUrl(String v)             { this.docPdfUrl = v; }

    public LocalDateTime getFechaRegistro()              { return fechaRegistro; }
    public void          setFechaRegistro(LocalDateTime v){ this.fechaRegistro = v; }

    public LocalDateTime getActualizadoEn()              { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v){ this.actualizadoEn = v; }

    public String getNombreCompleto() {
        return (nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "");
    }
}

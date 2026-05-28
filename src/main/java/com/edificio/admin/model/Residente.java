package com.edificio.admin.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Persona que reside en el edificio (arrendatario, codeudor, menor).
 * Corresponde a la tabla RESIDENTES.
 * La vinculacion con el apartamento se hace via CONTRATO_RESIDENTE -> CONTRATOS.
 */
public class Residente extends Persona {

    private String        email;
    private LocalDate     fechaNacimiento;
    private boolean       esMenorEdad;
    private boolean       activo;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private Integer       idApartamento;      // JOIN desde CONTRATOS (no persistido)
    private String        numeroApartamento;  // JOIN desde CONTRATOS -> APARTAMENTOS (no persistido)

    public Residente() { super(); }

    public Residente(Integer idResidente, Integer idTipoDoc, String numeroDocumento,
                     String nombres, String apellidos, String email, String telefono,
                     LocalDate fechaNacimiento, boolean esMenorEdad) {
        super(idResidente, idTipoDoc, numeroDocumento, nombres, apellidos, telefono);
        this.email           = email;
        this.fechaNacimiento = fechaNacimiento;
        this.esMenorEdad     = esMenorEdad;
        this.activo          = true;
    }

    // ---- getters / setters ----

    public String        getEmail()                         { return email; }
    public void          setEmail(String v)                 { this.email = v; }

    public LocalDate     getFechaNacimiento()               { return fechaNacimiento; }
    public void          setFechaNacimiento(LocalDate v)    { this.fechaNacimiento = v; }

    public boolean       isEsMenorEdad()                    { return esMenorEdad; }
    public void          setEsMenorEdad(boolean v)          { this.esMenorEdad = v; }

    public boolean       isActivo()                         { return activo; }
    public void          setActivo(boolean v)               { this.activo = v; }

    public LocalDateTime getCreadoEn()                      { return creadoEn; }
    public void          setCreadoEn(LocalDateTime v)       { this.creadoEn = v; }

    public LocalDateTime getActualizadoEn()                 { return actualizadoEn; }
    public void          setActualizadoEn(LocalDateTime v)  { this.actualizadoEn = v; }

    public Integer       getIdApartamento()                 { return idApartamento; }
    public void          setIdApartamento(Integer v)        { this.idApartamento = v; }

    public String        getNumeroApartamento()             { return numeroApartamento; }
    public void          setNumeroApartamento(String v)     { this.numeroApartamento = v; }

    public int getEdad() {
        if (fechaNacimiento == null) return 0;
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    public String getDescripcionMenor() {
        if (!esMenorEdad) return null;
        int edad = getEdad();
        if (edad >= 16 && edad < 18) {
            return "Menor de edad (16-17) autorizado para residir independientemente";
        }
        return "Menor de edad - requiere tutor legal";
    }
}

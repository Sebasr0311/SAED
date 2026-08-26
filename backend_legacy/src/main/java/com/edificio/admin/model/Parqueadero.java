package com.edificio.admin.model;

import com.edificio.admin.model.enums.TipoParqueadero;
import com.edificio.admin.model.enums.EstadoParqueadero;
import java.time.LocalDateTime;

/**
 * Puesto de parqueo del edificio.
 * Corresponde a la tabla PARQUEADEROS.
 * esVisitante=true => rotativo para visitas.
 *
 * numeroApartamento y nombrePropietario son campos calculados del JOIN
 * con APARTAMENTOS / CONTRATOS / RESIDENTES y no se persisten directamente.
 */
public class Parqueadero {

    private Integer           idParqueadero;
    private String            codigo;
    private TipoParqueadero   tipo;
    private boolean           esVisitante;
    private EstadoParqueadero estado;
    private boolean           activo;
    private LocalDateTime     actualizadoEn;

    // Campos de JOIN (solo lectura)
    private Integer           idApartamento;
    private String            numeroApartamento;
    private String            nombrePropietario;

    public Parqueadero() {}

    // ---- getters / setters ----

    public Integer            getIdParqueadero()                      { return idParqueadero; }
    public void               setIdParqueadero(Integer v)             { this.idParqueadero = v; }

    public String             getCodigo()                             { return codigo; }
    public void               setCodigo(String v)                     { this.codigo = v; }

    public TipoParqueadero    getTipo()                               { return tipo; }
    public void               setTipo(TipoParqueadero v)              { this.tipo = v; }

    public boolean            isEsVisitante()                         { return esVisitante; }
    public void               setEsVisitante(boolean v)               { this.esVisitante = v; }

    public EstadoParqueadero  getEstado()                             { return estado; }
    public void               setEstado(EstadoParqueadero v)          { this.estado = v; }

    public boolean            isActivo()                              { return activo; }
    public void               setActivo(boolean v)                    { this.activo = v; }

    public LocalDateTime      getActualizadoEn()                      { return actualizadoEn; }
    public void               setActualizadoEn(LocalDateTime v)       { this.actualizadoEn = v; }

    public Integer            getIdApartamento()                      { return idApartamento; }
    public void               setIdApartamento(Integer v)             { this.idApartamento = v; }

    public String             getNumeroApartamento()                  { return numeroApartamento; }
    public void               setNumeroApartamento(String v)          { this.numeroApartamento = v; }

    public String             getNombrePropietario()                  { return nombrePropietario; }
    public void               setNombrePropietario(String v)          { this.nombrePropietario = v; }

    @Override
    public String toString() { return codigo + " [" + (tipo != null ? tipo.name() : "?") + "]"; }
}

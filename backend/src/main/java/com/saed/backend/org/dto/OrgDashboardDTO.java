package com.saed.backend.org.dto;

import java.util.List;
import java.util.Map;

public class OrgDashboardDTO {
    private Map<String, Object> propiedades;
    private Map<String, Object> unidades;
    private Map<String, Object> administradores;
    private Map<String, Object> usuarios;
    private Map<String, Object> suscripcion;
    private Map<String, Object> finanzas;
    private List<Map<String, Object>> propiedadesRecientes;

    public OrgDashboardDTO() {}

    public OrgDashboardDTO(Map<String, Object> propiedades,
                           Map<String, Object> unidades,
                           Map<String, Object> administradores,
                           Map<String, Object> usuarios,
                           Map<String, Object> suscripcion,
                           Map<String, Object> finanzas,
                           List<Map<String, Object>> propiedadesRecientes) {
        this.propiedades = propiedades;
        this.unidades = unidades;
        this.administradores = administradores;
        this.usuarios = usuarios;
        this.suscripcion = suscripcion;
        this.finanzas = finanzas;
        this.propiedadesRecientes = propiedadesRecientes;
    }

    public Map<String, Object> getPropiedades() { return propiedades; }
    public void setPropiedades(Map<String, Object> propiedades) { this.propiedades = propiedades; }

    public Map<String, Object> getUnidades() { return unidades; }
    public void setUnidades(Map<String, Object> unidades) { this.unidades = unidades; }

    public Map<String, Object> getAdministradores() { return administradores; }
    public void setAdministradores(Map<String, Object> administradores) { this.administradores = administradores; }

    public Map<String, Object> getUsuarios() { return usuarios; }
    public void setUsuarios(Map<String, Object> usuarios) { this.usuarios = usuarios; }

    public Map<String, Object> getSuscripcion() { return suscripcion; }
    public void setSuscripcion(Map<String, Object> suscripcion) { this.suscripcion = suscripcion; }

    public Map<String, Object> getFinanzas() { return finanzas; }
    public void setFinanzas(Map<String, Object> finanzas) { this.finanzas = finanzas; }

    public List<Map<String, Object>> getPropiedadesRecientes() { return propiedadesRecientes; }
    public void setPropiedadesRecientes(List<Map<String, Object>> propiedadesRecientes) { this.propiedadesRecientes = propiedadesRecientes; }
}

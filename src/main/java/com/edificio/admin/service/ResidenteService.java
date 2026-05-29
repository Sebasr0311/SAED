package com.edificio.admin.service;

import com.edificio.admin.dao.ResidenteDAO;
import com.edificio.admin.dao.ApartamentoDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.Residente;
import com.edificio.admin.model.Apartamento;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Logica de negocio para RESIDENTES.
 */
public class ResidenteService {

    private final ResidenteDAO residenteDAO;
    private final ApartamentoDAO apartamentoDAO;

    public ResidenteService() {
        this.residenteDAO = new ResidenteDAO();
        this.apartamentoDAO = new ApartamentoDAO();
    }

    public List<Residente> listarTodos() throws SQLException {
        return residenteDAO.findAll();
    }

    public List<Residente> listarConUsuario() throws SQLException {
        return residenteDAO.findAllConUsuario();
    }

    public Residente buscarPorId(Integer id) throws SQLException {
        validarId(id);
        Residente r = residenteDAO.findById(id);
        if (r == null) throw new RegistroNoEncontradoException("Residente no encontrado: " + id);
        return r;
    }

    public List<Residente> buscarPorContrato(Integer idContrato) throws SQLException {
        if (idContrato == null || idContrato <= 0)
            throw new DatosInvalidosException("ID de contrato invalido.");
        return residenteDAO.findByContrato(idContrato);
    }

    public Integer registrar(Residente residente) throws SQLException {
        validar(residente);
        calcularEdad(residente);
        validarCapacidadApartamento(residente, null);
        return residenteDAO.insert(residente);
    }

    public void actualizar(Residente residente) throws SQLException {
        validarId(residente.getId());
        validar(residente);
        calcularEdad(residente);
        validarCapacidadApartamento(residente, residente.getId());
        residenteDAO.update(residente);
    }

    private void calcularEdad(Residente r) {
        if (r.getFechaNacimiento() != null) {
            int edad = Period.between(r.getFechaNacimiento(), LocalDate.now()).getYears();
            r.setEsMenorEdad(edad < 18);
        }
    }

    /** Soft-delete: activo = 0. */
    public void desactivar(Integer id) throws SQLException {
        validarId(id);
        residenteDAO.delete(id);
    }

    // ---- validaciones ----

    private void validar(Residente r) {
        if (r.getNombres() == null || r.getNombres().isBlank())
            throw new DatosInvalidosException("Los nombres son obligatorios.");
        if (r.getApellidos() == null || r.getApellidos().isBlank())
            throw new DatosInvalidosException("Los apellidos son obligatorios.");
        if (r.getNumeroDocumento() == null || r.getNumeroDocumento().isBlank())
            throw new DatosInvalidosException("El numero de documento es obligatorio.");
        if (r.getIdTipoDoc() == null || r.getIdTipoDoc() <= 0)
            throw new DatosInvalidosException("El tipo de documento es obligatorio.");
        if (r.getEmail() == null || r.getEmail().isBlank())
            throw new DatosInvalidosException("El email es obligatorio.");
        if (r.getFechaNacimiento() == null)
            throw new DatosInvalidosException(
                "La fecha de nacimiento es obligatoria.");
        if (r.getFechaNacimiento().isAfter(LocalDate.now()))
            throw new DatosInvalidosException(
                "La fecha de nacimiento no puede ser futura.");
        int edad = Period.between(r.getFechaNacimiento(), LocalDate.now()).getYears();
        if (edad < 16)
            throw new DatosInvalidosException(
                "El residente debe tener al menos 16 a\u00f1os.");
        if (edad > 110)
            throw new DatosInvalidosException(
                "La fecha de nacimiento no es v\u00e1lida (mayor de 110 a\u00f1os).");
    }

    private void validarId(Integer id) {
        if (id == null || id <= 0)
            throw new DatosInvalidosException("ID de residente invalido.");
    }

    private void validarCapacidadApartamento(Residente residente, Integer idResidenteActual) throws SQLException {
        if (residente.getIdApartamento() == null) {
            return; // No hay apartamento asignado, no validar
        }
        
        // Obtener información del apartamento
        Apartamento apartamento = apartamentoDAO.findById(residente.getIdApartamento());
        if (apartamento == null) {
            throw new DatosInvalidosException("El apartamento especificado no existe.");
        }
        
        // Usar la capacidad máxima configurada en el apartamento
        int capacidadMaxima = apartamento.getCapacidadMaxima() != null ? apartamento.getCapacidadMaxima() : 2;
        
        // Contar residentes activos en el apartamento
        List<Residente> residentes = residenteDAO.findAll();
        int residentesActuales = 0;
        for (Residente r : residentes) {
            if (r.isActivo() && 
                r.getIdApartamento() != null && 
                r.getIdApartamento().equals(residente.getIdApartamento())) {
                // Si estamos actualizando, no contar el residente actual
                if (idResidenteActual == null || !r.getId().equals(idResidenteActual)) {
                    residentesActuales++;
                }
            }
        }
        
        // Verificar que no se exceda la capacidad
        if (residentesActuales >= capacidadMaxima) {
            throw new DatosInvalidosException(
                "El apartamento " + apartamento.getNumero() + " ha alcanzado su capacidad máxima de " + 
                capacidadMaxima + " residentes. Actualmente hay " + residentesActuales + " residentes activos."
            );
        }
    }
}

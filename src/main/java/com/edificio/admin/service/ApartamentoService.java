package com.edificio.admin.service;

import com.edificio.admin.dao.ApartamentoDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.enums.EstadoApartamento;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Logica de negocio para APARTAMENTOS.
 */
public class ApartamentoService {

    private final ApartamentoDAO apartamentoDAO;

    public ApartamentoService() {
        this.apartamentoDAO = new ApartamentoDAO();
    }

    public List<Apartamento> listarTodos() throws SQLException {
        return apartamentoDAO.findAll();
    }

    public List<Apartamento> listarDisponibles() throws SQLException {
        return apartamentoDAO.findByEstado(EstadoApartamento.DISPONIBLE);
    }

    public Apartamento buscarPorId(Integer id) throws SQLException {
        validarId(id);
        Apartamento a = apartamentoDAO.findById(id);
        if (a == null) throw new RegistroNoEncontradoException("Apartamento no encontrado: " + id);
        return a;
    }

    public Integer registrar(Apartamento apartamento) throws SQLException {
        validar(apartamento);
        return apartamentoDAO.insert(apartamento);
    }

    public void actualizar(Apartamento apartamento) throws SQLException {
        validarId(apartamento.getIdApartamento());
        validar(apartamento);
        apartamentoDAO.update(apartamento);
    }

    /** Soft-delete: activo = 0. */
    public void desactivar(Integer id) throws SQLException {
        validarId(id);
        apartamentoDAO.delete(id);
    }

    // ---- validaciones ----

    private void validar(Apartamento a) {
        if (a.getNumero() == null || a.getNumero().isBlank())
            throw new DatosInvalidosException("El numero del apartamento es obligatorio.");
        if (a.getPiso() == null || a.getPiso() <= 0)
            throw new DatosInvalidosException("El piso debe ser un numero positivo.");
        if (a.getTipo() == null || a.getTipo().isBlank())
            throw new DatosInvalidosException("El tipo de apartamento es obligatorio.");
        if (a.getAreaM2() == null || a.getAreaM2().compareTo(BigDecimal.ZERO) <= 0)
            throw new DatosInvalidosException("El area debe ser mayor que 0.");
        
        // Validar capacidad máxima según tipo de apartamento
        if (a.getCapacidadMaxima() != null) {
            int maxPermitido = getCapacidadMaximaPorTipo(a.getTipo());
            if (a.getCapacidadMaxima() < 1) {
                throw new DatosInvalidosException("La capacidad maxima debe ser al menos 1.");
            }
            if (a.getCapacidadMaxima() > maxPermitido) {
                throw new DatosInvalidosException(
                    String.format("La capacidad maxima para apartamento tipo %s no puede exceder %d personas.", 
                    a.getTipo(), maxPermitido)
                );
            }
        }
    }
    
    /**
     * Retorna la capacidad máxima permitida según el tipo de apartamento
     */
    private int getCapacidadMaximaPorTipo(String tipo) {
        if (tipo == null) return 2;
        switch (tipo.toUpperCase()) {
            case "ESTUDIO": return 2;
            case "1HAB":
            case "1_HAB": return 3;
            case "2HAB":
            case "2_HAB": return 5;
            case "3HAB":
            case "3_HAB": return 7;
            case "PENTHOUSE": return 8;
            default: return 2;
        }
    }

    private void validarId(Integer id) {
        if (id == null || id <= 0)
            throw new DatosInvalidosException("ID de apartamento invalido.");
    }
}

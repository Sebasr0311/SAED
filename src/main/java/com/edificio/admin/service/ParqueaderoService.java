package com.edificio.admin.service;

import com.edificio.admin.dao.ParqueaderoDAO;
import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.exception.RegistroNoEncontradoException;
import com.edificio.admin.model.Parqueadero;

import java.sql.SQLException;
import java.util.List;

/**
 * Logica de negocio para PARQUEADEROS.
 * El estado DISPONIBLE/OCUPADO se gestiona via TRG_ACCESO_SALIDA
 * al registrar la salida de una visita. Este servicio solo administra
 * el catalogo de parqueaderos.
 */
public class ParqueaderoService {

    private final ParqueaderoDAO parqueaderoDAO;

    public ParqueaderoService() {
        this.parqueaderoDAO = new ParqueaderoDAO();
    }

    public List<Parqueadero> listarTodos() throws SQLException {
        return parqueaderoDAO.findAll();
    }

    public List<Parqueadero> listarDisponiblesParaVisitantes() throws SQLException {
        return parqueaderoDAO.findDisponiblesParaVisitantes();
    }

    public Parqueadero buscarPorId(Integer id) throws SQLException {
        validarId(id);
        Parqueadero p = parqueaderoDAO.findById(id);
        if (p == null) throw new RegistroNoEncontradoException("Parqueadero no encontrado: " + id);
        return p;
    }

    public Integer registrar(Parqueadero parqueadero) throws SQLException {
        validar(parqueadero);
        return parqueaderoDAO.insert(parqueadero);
    }

    public void actualizar(Parqueadero parqueadero) throws SQLException {
        validarId(parqueadero.getIdParqueadero());
        validar(parqueadero);
        parqueaderoDAO.update(parqueadero);
    }

    /** Soft-delete: activo = 0. */
    public void desactivar(Integer id) throws SQLException {
        validarId(id);
        parqueaderoDAO.delete(id);
    }

    // ---- validaciones ----

    private void validar(Parqueadero p) {
        if (p.getCodigo() == null || p.getCodigo().isBlank())
            throw new DatosInvalidosException("El codigo del parqueadero es obligatorio.");
        if (p.getTipo() == null)
            throw new DatosInvalidosException("El tipo de parqueadero es obligatorio.");
    }

    private void validarId(Integer id) {
        if (id == null || id <= 0)
            throw new DatosInvalidosException("ID de parqueadero invalido.");
    }
}

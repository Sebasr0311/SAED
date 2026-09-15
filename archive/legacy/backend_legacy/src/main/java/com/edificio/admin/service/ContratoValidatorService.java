package com.edificio.admin.service;

import com.edificio.admin.exception.DatosInvalidosException;
import com.edificio.admin.dao.ApartamentoDAO;
import com.edificio.admin.dao.ContratoDAO;
import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.enums.EstadoApartamento;
import com.edificio.admin.model.enums.EstadoContrato;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

public class ContratoValidatorService {

    private final ApartamentoDAO apartamentoDAO;
    private final ContratoDAO contratoDAO;

    public ContratoValidatorService() {
        this.apartamentoDAO = new ApartamentoDAO();
        this.contratoDAO = new ContratoDAO();
    }

    public void validarCreacion(Contrato contrato, Integer idResidente) throws SQLException {
        if (idResidente == null || idResidente <= 0)
            throw new DatosInvalidosException("ID del residente arrendatario es obligatorio.");
        if (contrato.getIdApartamento() == null || contrato.getIdApartamento() <= 0)
            throw new DatosInvalidosException("El apartamento es obligatorio.");

        Apartamento apto = apartamentoDAO.findById(contrato.getIdApartamento());
        if (apto == null)
            throw new DatosInvalidosException("El apartamento no existe.");
        if (apto.getEstado() == EstadoApartamento.OCUPADO)
            throw new DatosInvalidosException("El apartamento ya est\u00e1 ocupado.");

        if (contrato.getFechaInicio() == null)
            throw new DatosInvalidosException("La fecha de inicio es obligatoria.");
        if (contrato.getFechaInicio().isBefore(LocalDate.now()))
            throw new DatosInvalidosException("La fecha de inicio no puede ser anterior a hoy.");
        if (contrato.getFechaFin() != null && !contrato.getFechaFin().isAfter(contrato.getFechaInicio()))
            throw new DatosInvalidosException("La fecha de fin debe ser posterior a la fecha de inicio.");
        if (contrato.getValorMensual() == null || contrato.getValorMensual().compareTo(BigDecimal.ZERO) <= 0)
            throw new DatosInvalidosException("El valor mensual debe ser mayor que 0.");
    }

    public void validarRenovacion(Contrato contrato, LocalDate nuevaInicio, LocalDate nuevaFin) {
        if (contrato.getEstado() != EstadoContrato.VENCIDO)
            throw new DatosInvalidosException("Solo se pueden renovar contratos vencidos.");
        if (nuevaInicio == null)
            throw new DatosInvalidosException("La nueva fecha de inicio es obligatoria.");
        if (nuevaFin != null && !nuevaFin.isAfter(nuevaInicio))
            throw new DatosInvalidosException("La fecha de fin debe ser posterior a la fecha de inicio.");
    }
}

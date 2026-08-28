package com.saed.backend.finanzas.service;

import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FlujoCajaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FlujoCajaService {

    private final FlujoCajaRepository repo;

    public FlujoCajaService(FlujoCajaRepository repo) {
        this.repo = repo;
    }

    public FlujoCajaResumenDTO getResumen() {
        BigDecimal saldo = repo.getSaldoActual();
        BigDecimal ingresos = repo.getTotalIngresos();
        BigDecimal egresos = repo.getTotalEgresos();
        BigDecimal esperados = repo.getIngresosEsperados();
        BigDecimal programados = repo.getGastosProgramados();
        BigDecimal proyeccion = saldo.add(esperados).subtract(programados);

        return new FlujoCajaResumenDTO(saldo, ingresos, egresos, esperados, programados, proyeccion);
    }

    public List<FlujoCajaMovimientoDTO> getMovimientosRecientes(int limite) {
        return repo.getMovimientosRecientes(limite);
    }

    public List<FlujoCajaMovimientoDTO> getProyeccion() {
        return repo.getProyeccionMensual();
    }
}

package com.saed.backend.trabajadores.service;

import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorCreateDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorUpdateDTO;

import java.util.List;

public interface TrabajadorService {

    TrabajadorDTO crearTrabajador(TrabajadorCreateDTO dto);

    List<TrabajadorDTO> listarTrabajadores(Long idProveedor, String estado, String search);

    TrabajadorDTO obtenerTrabajador(Long idTrabajador);

    TrabajadorDTO actualizarTrabajador(Long idTrabajador, TrabajadorUpdateDTO dto);

    void cambiarEstado(Long idTrabajador, String nuevoEstado);

    List<ObraTrabajadorDTO> listarTrabajadoresObra(Long idObra);

    void asignarTrabajadorObra(Long idObra, Long idTrabajador, String autorizado);

    void autorizarTrabajadorObra(Long idObra, Long idTrabajador);

    void revocarTrabajadorObra(Long idObra, Long idTrabajador);

    void desasignarTrabajadorObra(Long idObra, Long idTrabajador);

    void validarTrabajadoresParaEjecucionObra(Long idObra);
}

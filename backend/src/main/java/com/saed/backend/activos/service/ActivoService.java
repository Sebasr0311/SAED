package com.saed.backend.activos.service;

import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.dto.ActivoUpdateDTO;

import java.util.List;

public interface ActivoService {

    ActivoDTO crear(ActivoCreateDTO dto);

    List<ActivoDTO> listar(String estado, String categoria, String search);

    ActivoDTO obtenerPorId(Long idActivo);

    ActivoDTO actualizar(Long idActivo, ActivoUpdateDTO dto);

    void actualizarEstado(Long idActivo, String nuevoEstado);

    void darDeBaja(Long idActivo);
}

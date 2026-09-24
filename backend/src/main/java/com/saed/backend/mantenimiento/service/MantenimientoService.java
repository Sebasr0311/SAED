package com.saed.backend.mantenimiento.service;

import com.saed.backend.mantenimiento.dto.MantenimientoCreateDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoEstadoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoReprogramarDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoUpdateDTO;

import java.util.List;
import java.util.Map;

public interface MantenimientoService {

    List<MantenimientoDTO> listar(String estado, String tipo, String prioridad, Long idActivo, String search);

    MantenimientoDTO obtenerPorId(Long id);

    MantenimientoDTO crear(MantenimientoCreateDTO dto);

    MantenimientoDTO actualizar(Long id, MantenimientoUpdateDTO dto);

    MantenimientoDTO cambiarEstado(Long id, MantenimientoEstadoDTO dto);

    MantenimientoDTO reprogramar(Long id, MantenimientoReprogramarDTO dto);

    MantenimientoDTO cancelar(Long id, String motivo);

    Map<String, Object> obtenerKpis();
}

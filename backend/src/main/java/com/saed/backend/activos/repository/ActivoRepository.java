package com.saed.backend.activos.repository;

import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.dto.ActivoUpdateDTO;

import java.util.List;
import java.util.Optional;

public interface ActivoRepository {

    ActivoDTO crear(Long idPropiedad, ActivoCreateDTO dto, String estadoFinal);

    List<ActivoDTO> listar(Long idPropiedad, String estado, String categoria, String search);

    Optional<ActivoDTO> buscarPorId(Long idActivo, Long idPropiedad);

    Optional<ActivoDTO> buscarPorIdDirecto(Long idActivo);

    boolean existePorCodigo(Long idPropiedad, String codigoActivo, Long excluirIdActivo);

    void actualizar(Long idActivo, Long idPropiedad, ActivoUpdateDTO dto);

    void actualizarEstado(Long idActivo, Long idPropiedad, String nuevoEstado);
}

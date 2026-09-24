package com.saed.backend.trabajadores.repository;

import com.saed.backend.trabajadores.dto.TrabajadorCreateDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorUpdateDTO;

import java.util.List;
import java.util.Optional;

public interface TrabajadorRepository {

    TrabajadorDTO crear(Long idPersona, TrabajadorCreateDTO dto);

    List<TrabajadorDTO> listar(Long idOrganizacion, Long idProveedor, String estado, String search);

    Optional<TrabajadorDTO> buscarPorId(Long idTrabajador, Long idOrganizacion);

    Optional<TrabajadorDTO> buscarPorIdDirecto(Long idTrabajador);

    Optional<TrabajadorDTO> buscarPorIdForUpdate(Long idTrabajador);

    boolean existePorProveedorYPersona(Long idProveedor, Long idPersona, Long excluirIdTrabajador);

    void actualizar(Long idTrabajador, Long idOrganizacion, TrabajadorUpdateDTO dto);

    void actualizarEstado(Long idTrabajador, Long idOrganizacion, String nuevoEstado);
}

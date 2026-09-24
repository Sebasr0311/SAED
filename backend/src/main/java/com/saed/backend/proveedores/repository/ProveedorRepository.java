package com.saed.backend.proveedores.repository;

import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.dto.ProveedorUpdateDTO;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para el Catálogo Maestro de Proveedores (GAP-F9-01).
 * Toda consulta y mutación está estrictamente acotada por ID_ORGANIZACION (Tenant Isolation).
 */
public interface ProveedorRepository {

    ProveedorDTO crear(Long idOrganizacion, ProveedorCreateDTO dto);

    List<ProveedorDTO> listar(Long idOrganizacion, String estado, String categoria, String search);

    Optional<ProveedorDTO> buscarPorId(Long idProveedor, Long idOrganizacion);

    Optional<ProveedorDTO> buscarPorIdDirecto(Long idProveedor);

    boolean existePorNit(Long idOrganizacion, String nitIdentificacion, Long excluirIdProveedor);

    void actualizar(Long idProveedor, Long idOrganizacion, ProveedorUpdateDTO dto);

    void actualizarEstado(Long idProveedor, Long idOrganizacion, String nuevoEstado);
}

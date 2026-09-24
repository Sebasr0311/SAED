package com.saed.backend.proveedores.service;

import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.dto.ProveedorUpdateDTO;

import java.util.List;

/**
 * Servicio de negocio para la gestión del Catálogo Maestro de Proveedores y Contratistas (GAP-F9-01).
 */
public interface ProveedorService {

    ProveedorDTO crear(ProveedorCreateDTO dto);

    List<ProveedorDTO> listar(String estado, String categoria, String search);

    ProveedorDTO obtenerPorId(Long idProveedor);

    ProveedorDTO actualizar(Long idProveedor, ProveedorUpdateDTO dto);

    void actualizarEstado(Long idProveedor, String nuevoEstado);
}

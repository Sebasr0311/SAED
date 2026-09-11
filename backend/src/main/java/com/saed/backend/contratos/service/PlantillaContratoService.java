package com.saed.backend.contratos.service;

import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import java.util.List;
import java.util.Map;

public interface PlantillaContratoService {

    List<PlantillaContratoDTO> listarPorOrganizacion(String estado);

    List<PlantillaContratoDTO> listarActivasParaPropiedad();

    PlantillaContratoDTO obtenerPorId(Long id);

    PlantillaContratoDTO crear(PlantillaContratoRequestDTO dto);

    PlantillaContratoDTO actualizar(Long id, PlantillaContratoRequestDTO dto);

    PlantillaContratoDTO crearNuevaVersion(Long id, PlantillaContratoRequestDTO dto);

    void cambiarEstado(Long id, String nuevoEstado);

    String renderizarPlantilla(Long idPlantilla, Map<String, Object> variables);

    List<String> getVariablesSoportadas();
}

package com.saed.backend.emergencias.repository;

import com.saed.backend.emergencias.dto.ContactoEmergenciaDTO;
import com.saed.backend.emergencias.dto.ContactoEmergenciaRequestDTO;

import java.util.List;
import java.util.Optional;

public interface ContactoEmergenciaRepository {
    List<ContactoEmergenciaDTO> findAllByPropiedad(Long idPropiedad);
    List<ContactoEmergenciaDTO> findPrioritariosByPropiedad(Long idPropiedad);
    Optional<ContactoEmergenciaDTO> findByIdAndPropiedad(Long idContactoEmergencia, Long idPropiedad);
    Long insert(Long idPropiedad, ContactoEmergenciaRequestDTO dto);
    void update(Long idContactoEmergencia, Long idPropiedad, ContactoEmergenciaRequestDTO dto);
    void delete(Long idContactoEmergencia, Long idPropiedad);
    int countTotalByPropiedad(Long idPropiedad);
    int countPrioritariosByPropiedad(Long idPropiedad);
}

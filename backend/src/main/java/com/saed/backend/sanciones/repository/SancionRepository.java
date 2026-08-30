package com.saed.backend.sanciones.repository;

import com.saed.backend.sanciones.dto.SancionDTO;
import java.util.List;
import java.util.Optional;

public interface SancionRepository {
    List<SancionDTO> findAllSanciones();
    List<SancionDTO> findSancionesByPersona(Long idPersona);
    Optional<SancionDTO> findById(Long idSancion, Long idPropiedad);
    Long createSancion(SancionDTO sancion, Long idPropiedad, Long creadoPor);
    void updateEstado(Long idSancion, Long idPropiedad, String estado, String resolucionFinal);
    void saveDescargos(Long idSancion, Long idPersonaPresenta, String argumentos, Long radicadoPorUsuario);
    Long getIdPersonaFromUsuario(Long idUsuario);
}

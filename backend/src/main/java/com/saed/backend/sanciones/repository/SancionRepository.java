package com.saed.backend.sanciones.repository;

import com.saed.backend.sanciones.dto.DescargoDTO;
import com.saed.backend.sanciones.dto.SancionCreateRequestDTO;
import com.saed.backend.sanciones.dto.SancionDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SancionRepository {
    List<SancionDTO> findAllByPropiedad(Long idPropiedad);
    List<SancionDTO> findByPersonaOUnidades(Long idPersona, List<Long> unidadesIds, Long idPropiedad);
    Optional<SancionDTO> findById(Long idSancion);
    Long crearSancion(SancionCreateRequestDTO request, Long idPropiedad, Long idUsuarioCreador, String numeroExpediente, LocalDate fechaLimiteDescargos);
    Long registrarDescargo(Long idSancion, Long idPersona, Long idUsuario, String argumentos, String pruebasUrl);
    void actualizarEstado(Long idSancion, String nuevoEstado);
    void emitirResolucion(Long idSancion, String decision, String resolucionFinal);
    List<DescargoDTO> findDescargosBySancion(Long idSancion);
    String generarSiguienteExpediente(Long idPropiedad);
    Optional<Long> findConceptoMulta(Long idPropiedad);
    void crearMultaDesdeSancion(Long idSancion, Long idUnidad, Long idPersona, Long idConcepto, BigDecimal monto, String motivo, Long idUsuario);
}

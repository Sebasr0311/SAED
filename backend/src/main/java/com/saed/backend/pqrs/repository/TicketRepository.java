package com.saed.backend.pqrs.repository;

import com.saed.backend.pqrs.dto.PqrsSlaConfigDTO;
import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import com.saed.backend.pqrs.dto.TicketTrazabilidadDTO;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketRepository {
    List<TicketResponseDTO> findAll(Long idPropiedad);
    List<TicketResponseDTO> findByPersona(Long idPropiedad, Long idUsuario);
    Optional<TicketResponseDTO> findById(Long idTicket, Long idPropiedad);
    Optional<TicketResponseDTO> findByIdGlobal(Long idTicket);
    Long create(TicketRequestDTO request, Long idPropiedad, Long idUnidad, Long idUsuario, String numeroRadicado, ZonedDateTime fechaRadicacion, ZonedDateTime fechaLimiteSla);
    int updateEstado(Long idTicket, Long idPropiedad, String estadoAnterior, String nuevoEstado, String observacionCierre);
    int asignarResponsable(Long idTicket, Long idPropiedad, Long idResponsable);
    int actualizarPrioridad(Long idTicket, Long idPropiedad, String prioridad, ZonedDateTime nuevaFechaSla);
    void insertTrazabilidad(Long idTicket, Long idUsuario, String tipoIntervencion, String estadoAnterior, String estadoNuevo, String comentario, String adjuntoUrl);
    List<TicketTrazabilidadDTO> findTrazabilidadByTicket(Long idTicket);
    Optional<Integer> getSlaHoras(Long idPropiedad, String prioridad);
    List<PqrsSlaConfigDTO> getSlaConfigs(Long idPropiedad);
    void upsertSlaConfig(Long idPropiedad, String prioridad, int tiempoMaximoHoras, int alertaHoras);
    Long getIdPersonaFromUsuario(Long idUsuario);
    Long getIdUnidadFromUsuario(Long idUsuario, Long idPropiedad);
}

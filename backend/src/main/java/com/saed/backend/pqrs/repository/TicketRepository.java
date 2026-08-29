package com.saed.backend.pqrs.repository;

import com.saed.backend.pqrs.dto.TicketRequestDTO;
import com.saed.backend.pqrs.dto.TicketResponseDTO;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketRepository {
    List<TicketResponseDTO> findAll();
    List<TicketResponseDTO> findByPersona(Long idPersona);
    Optional<TicketResponseDTO> findById(Long idTicket);
    Long create(TicketRequestDTO request, Long idPropiedad, Long idPersonaRadica, String numeroRadicado, ZonedDateTime fechaLimiteSla);
    void updateEstado(Long idTicket, String estado);
}

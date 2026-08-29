package com.saed.backend.reservas.repository;

import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import java.util.List;
import java.util.Optional;

public interface ReservasRepository {
    List<ZonaComunDTO> findAllZonas();
    List<ReservaDTO> findAllReservas();
    List<ReservaDTO> findReservasByPersona(Long idPersona);
    Optional<ReservaDTO> findReservaById(Long idReserva);
    Long createReserva(ReservaDTO reserva, Long idPropiedad);
    void updateEstadoReserva(Long idReserva, String estado, Long aprobadoPor);
}

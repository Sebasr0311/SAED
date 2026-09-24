package com.saed.backend.reservas.repository;

import com.saed.backend.reservas.dto.CreateZonaComunDTO;
import com.saed.backend.reservas.dto.ReservaDTO;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.dto.ZonaComunDTO;
import java.util.List;
import java.util.Optional;

public interface ReservasRepository {
    List<ZonaComunDTO> findAllZonas();
    List<ReservaDTO> findAllReservas();
    List<ReservaDTO> findReservasByPersona(Long idUsuario);
    Optional<ReservaDTO> findReservaById(Long idReserva);
    Long createReserva(ReservaDTO reserva, Long idPropiedad);
    void updateEstadoReserva(Long idReserva, String estado, Long aprobadoPor);
    boolean existsZonaInPropiedad(Long idZona, Long idPropiedad);
    boolean existsUnidadInPropiedad(Long idUnidad, Long idPropiedad);
    List<ZonaComunDTO> findZonasByPropiedad(Long idPropiedad);
    List<ReservaDTO> findReservasByPropiedad(Long idPropiedad);
    List<ReservaDTO> findReservasByUnidad(Long idUnidad);
    boolean lockZonaForUpdate(Long idZona, Long idPropiedad);
    boolean hasOverlappingReserva(Long idZona, java.time.LocalDate fechaReserva, String horaInicio, String horaFin);
    boolean hasOverlappingBloqueo(Long idZona, java.time.LocalDate fechaReserva, String horaInicio, String horaFin);
    boolean lockReservaForUpdate(Long idReserva);

    // Operaciones CRUD Zonas Comunes (GAP-F8-05)
    Optional<ZonaComunDTO> findZonaByIdAndPropiedad(Long idZona, Long idPropiedad);
    Long saveZona(CreateZonaComunDTO dto, Long idPropiedad);
    boolean updateZona(Long idZona, Long idPropiedad, UpdateZonaComunDTO dto);
    boolean softDeleteZona(Long idZona, Long idPropiedad);
    boolean existsZonaNombreInPropiedad(String nombre, Long idPropiedad, Long excludeIdZona);
}

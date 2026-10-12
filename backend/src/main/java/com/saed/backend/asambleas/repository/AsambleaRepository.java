package com.saed.backend.asambleas.repository;

import com.saed.backend.asambleas.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AsambleaRepository {

    List<AsambleaDTO> findAllByPropiedad(Long idPropiedad);

    Optional<AsambleaDTO> findById(Long idAsamblea);

    Long createAsamblea(AsambleaCreateRequestDTO request, Long idPropiedad, Long idUsuario);

    void updateEstado(Long idAsamblea, String nuevoEstado);

    void updateQuorumAlcanzado(Long idAsamblea, BigDecimal quorumAlcanzadoPct);

    List<AsistenciaDTO> findAsistencias(Long idAsamblea);

    Long registrarAsistencia(Long idAsamblea, AsistenciaRequestDTO request, BigDecimal coeficiente);

    int retirarAsistencia(Long idAsamblea, Long idUnidad);

    BigDecimal calcularQuorumAlcanzadoPct(Long idAsamblea, Long idPropiedad);

    List<PoderDTO> findPoderes(Long idAsamblea);

    Long registrarPoder(Long idAsamblea, PoderRequestDTO request);

    /** Decides a pending power of the given assembly only; returns the rows changed (0 when it is not pending there). */
    int decidirPoder(Long idAsamblea, Long idPoder, String nuevoEstado, Long idUsuarioValidador);

    /** Whether the assembly has a power with that id (any state). */
    boolean existePoder(Long idAsamblea, Long idPoder);

    List<VotacionDTO> findVotaciones(Long idAsamblea);

    Optional<VotacionDTO> findVotacionById(Long idVotacion);

    Long createVotacion(Long idAsamblea, VotacionCreateRequestDTO request);

    /**
     * Closes an ABIERTA voting and stores its computed outcome in one statement; returns the rows changed
     * (0 when the voting is no longer open).
     */
    int cerrarVotacion(Long idVotacion, VotacionResultado resultado);

    /** Summed coefficient of the property's active units (the coefficient base of the voting result). */
    BigDecimal sumCoeficientesPropiedad(Long idPropiedad);

    /** Coefficients represented in the assembly: attendance rows still present, proxies included (Ley 675 art. 45). */
    BigDecimal sumCoeficientesRepresentados(Long idAsamblea);

    Long registrarVoto(Long idVotacion, VotoRequestDTO request, BigDecimal coeficiente);

    /** Registered unit coefficient, or {@code null} when missing or not positive. */
    BigDecimal findCoeficienteUnidad(Long idUnidad);

    /**
     * Weight recorded for the unit's current attendance in the assembly, or {@code null} when absent or not positive.
     * Readable by any participant of the assembly's property, unlike the unit row itself.
     */
    BigDecimal findCoeficienteAsistencia(Long idAsamblea, Long idUnidad);

    boolean existeAsistenciaUnidad(Long idAsamblea, Long idUnidad);

    boolean existeVotoUnidad(Long idVotacion, Long idUnidad);

    boolean existePoderUnidad(Long idAsamblea, Long idUnidad);

    void updateAsamblea(Long idAsamblea, AsambleaUpdateRequestDTO request);

    void lockAsambleaForUpdate(Long idAsamblea);

    Long findPropiedadUnidad(Long idUnidad);

    Long findOrganizacionPropiedad(Long idPropiedad);

    int countUnidadesActivas(Long idPropiedad);

    void lockVotacionForUpdate(Long idVotacion);

    void anularVotacion(Long idVotacion);

    boolean esApoderadoAprobado(Long idAsamblea, Long idUnidad, Long idPersonaApoderado);

    Long findPersonaUsuario(Long idUsuario);

    /** Active legal ownership (PROPIETARIOS_UNIDAD ESTADO = 'ACTIVO' and not ended). */
    boolean esPropietarioLegalActivo(Long idUnidad, Long idPersona);

    /** Whether the persona exists in the organization (PERSONAS has no RLS, so the organization is checked explicitly). */
    boolean personaPerteneceAOrganizacion(Long idPersona, Long idOrganizacion);

    /** The unit has an attendance row in the assembly that has not been withdrawn. */
    boolean tieneAsistenciaVigente(Long idAsamblea, Long idUnidad);

    /** The given power is APROBADO for this assembly and unit and names this person as proxy. */
    boolean esPoderAprobadoDe(Long idPoder, Long idAsamblea, Long idUnidad, Long idPersonaApoderado);
}

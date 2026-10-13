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
    List<DescargoDTO> findDescargosBySancion(Long idSancion);
    String generarSiguienteExpediente(Long idPropiedad);
    Optional<Long> findConceptoMulta(Long idPropiedad);
    Long crearMultaDesdeSancion(Long idSancion, Long idUnidad, Long idPersona, Long idConcepto, BigDecimal monto, String motivo, Long idUsuario);

    /**
     * Records the resolution only while the file is still open (NOTIFICADA, EN_DESCARGOS,
     * EN_DELIBERACION_CONSEJO). Returns the number of rows changed: 0 means another request already
     * closed it, so no effect may be applied.
     */
    int emitirResolucionSiAbierta(Long idSancion, String decision, String resolucionFinal,
                                  LocalDate restriccionDesde, LocalDate restriccionHasta);

    Optional<Long> findMultaPorSancion(Long idSancion);

    void vincularCuotaMulta(Long idMulta, Long idCuota);

    /** APLICADA common-area suspension of the unit whose restriction period contains the date. */
    boolean existeRestriccionZonasComunes(Long idUnidad, LocalDate fecha);

    Long findPersonaUsuario(Long idUsuario);

    /** The person is the unit's active legal owner or active resident. */
    boolean personaVinculadaAUnidad(Long idPersona, Long idUnidad);

    List<Long> findUnidadesPersona(Long idPersona);

    Long findPropiedadUnidad(Long idUnidad);
}

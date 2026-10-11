package com.saed.backend.proveedores.contratos;

import java.time.LocalDate;
import java.util.List;

public interface AvisoRenovacionRepository {

    /** VIGENTE contracts of the property whose notice window (FECHA_FIN - DIAS_ALERTA_VENC) has opened on {@code fecha}. */
    List<ContratoPorVencerDTO> contratosPorVencer(Long idPropiedad, LocalDate fecha);

    boolean existeAviso(Long idContratoProveedor, String periodo);

    /**
     * Records the notice for one contract and period. The unique index UQ_AVISO_RENOV_PERIODO makes a
     * concurrent duplicate fail with {@link org.springframework.dao.DuplicateKeyException}.
     */
    Long registrarAviso(ContratoPorVencerDTO contrato, String periodo);

    void crearAlertaAdmin(Long idPropiedad, String mensaje);
}

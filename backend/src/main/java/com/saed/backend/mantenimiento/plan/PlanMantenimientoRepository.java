package com.saed.backend.mantenimiento.plan;

import java.time.LocalDate;
import java.util.List;

public interface PlanMantenimientoRepository {

    Long crear(PlanMantenimientoDTO plan, Long idPropiedad, Long creadoPor);

    List<PlanMantenimientoDTO> listar(Long idPropiedad);

    List<PlanMantenimientoDTO> listarActivos(Long idPropiedad);

    int desactivar(Long idPlan, Long idPropiedad);

    boolean existeOrden(Long idPlan, String periodo);

    /**
     * Inserts the scheduled order of the plan for one period. The unique index UQ_MANT_PLAN_PERIODO makes a
     * concurrent duplicate fail with {@link org.springframework.dao.DuplicateKeyException}.
     */
    Long crearOrden(PlanMantenimientoDTO plan, LocalDate fechaProgramada, String periodo);
}

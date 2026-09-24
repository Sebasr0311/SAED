package com.saed.backend.activos.service.impl;

import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.dto.ActivoUpdateDTO;
import com.saed.backend.activos.exception.ActivoEstadoInvalidoException;
import com.saed.backend.activos.exception.ActivoNoEncontradoException;
import com.saed.backend.activos.exception.CodigoActivoDuplicadoException;
import com.saed.backend.activos.exception.TransicionEstadoActivoInvalidaException;
import com.saed.backend.activos.repository.ActivoRepository;
import com.saed.backend.activos.service.ActivoService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ActivoServiceImpl implements ActivoService {

    private static final Logger log = LoggerFactory.getLogger(ActivoServiceImpl.class);
    private static final Set<String> ESTADOS_VALIDOS = Set.of("OPERATIVO", "MANTENIMIENTO", "DADO_DE_BAJA");

    private final ActivoRepository activoRepo;

    public ActivoServiceImpl(ActivoRepository activoRepo) {
        this.activoRepo = activoRepo;
    }

    /**
     * Resuelve el ID de la propiedad autenticada de forma determinista y a prueba de IDOR.
     * Nunca confía en payloads de cliente; extrae exclusivamente de SaedContextHolder.
     */
    private Long resolvePropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new AccessDeniedException("No se encontró contexto de seguridad en la sesión.");
        }
        if (ctx.getPropertyId() == null) {
            throw new AccessDeniedException("No se encontró una propiedad activa en el contexto de seguridad.");
        }
        return ctx.getPropertyId();
    }

    private void validarValorAdquisicion(BigDecimal valor) {
        if (valor != null && valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El valor de adquisición no puede ser negativo (CK_ACTIVOS_VALOR).");
        }
    }

    private String validarYNormalizarEstado(String estado, boolean defaultOperativo) {
        if (estado == null || estado.isBlank()) {
            if (defaultOperativo) {
                return "OPERATIVO";
            }
            throw new ActivoEstadoInvalidoException("El estado es obligatorio.");
        }
        String normalizado = estado.trim().toUpperCase();
        if (!ESTADOS_VALIDOS.contains(normalizado)) {
            throw new ActivoEstadoInvalidoException(
                "Estado no válido: '" + estado + "'. Los estados permitidos son: " + ESTADOS_VALIDOS
            );
        }
        return normalizado;
    }

    @Override
    @Transactional
    public ActivoDTO crear(ActivoCreateDTO dto) {
        Long propId = resolvePropertyId();

        // 1. Validar valor de adquisición no negativo
        validarValorAdquisicion(dto.valorAdquisicion());

        // 2. Validar y normalizar estado
        String estadoFinal = validarYNormalizarEstado(dto.estado(), true);

        // 3. Validar unicidad de código dentro de la propiedad (UIX_ACTIVO_CODIGO)
        if (activoRepo.existePorCodigo(propId, dto.codigoActivo(), null)) {
            throw new CodigoActivoDuplicadoException(
                "Ya existe un activo registrado con el código '" + dto.codigoActivo().trim() +
                "' en esta copropiedad."
            );
        }

        log.info("[Activos] Creando activo '{}' (Código: {}) para propiedad {}",
                dto.nombre(), dto.codigoActivo(), propId);

        return activoRepo.crear(propId, dto, estadoFinal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivoDTO> listar(String estado, String categoria, String search) {
        Long propId = resolvePropertyId();
        return activoRepo.listar(propId, estado, categoria, search);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivoDTO obtenerPorId(Long idActivo) {
        Long propId = resolvePropertyId();

        return activoRepo.buscarPorId(idActivo, propId)
                .orElseGet(() -> {
                    // Verificación Anti-IDOR: Si existe en otra propiedad, rechazar con 403
                    if (activoRepo.buscarPorIdDirecto(idActivo).isPresent()) {
                        log.warn("[Anti-IDOR] Intento de acceso denegado: Activo {} solicitado por propiedad {}", idActivo, propId);
                        throw new AccessDeniedException("Acceso denegado: El activo " + idActivo + " pertenece a otra propiedad.");
                    }
                    throw new ActivoNoEncontradoException("Activo no encontrado con ID: " + idActivo);
                });
    }

    @Override
    @Transactional
    public ActivoDTO actualizar(Long idActivo, ActivoUpdateDTO dto) {
        Long propId = resolvePropertyId();

        // 1. Validar valor de adquisición no negativo
        validarValorAdquisicion(dto.valorAdquisicion());

        // 2. Verificar existencia y pertenencia a la propiedad (Anti-IDOR)
        obtenerPorId(idActivo);

        // 3. Validar unicidad de código excluyendo el ID actual
        if (activoRepo.existePorCodigo(propId, dto.codigoActivo(), idActivo)) {
            throw new CodigoActivoDuplicadoException(
                "Ya existe otro activo registrado con el código '" + dto.codigoActivo().trim() +
                "' en esta copropiedad."
            );
        }

        activoRepo.actualizar(idActivo, propId, dto);

        return activoRepo.buscarPorId(idActivo, propId)
                .orElseThrow(() -> new IllegalStateException("No se pudo recuperar el activo actualizado con ID: " + idActivo));
    }

    @Override
    @Transactional
    public void actualizarEstado(Long idActivo, String nuevoEstado) {
        String targetEstado = validarYNormalizarEstado(nuevoEstado, false);
        Long propId = resolvePropertyId();

        ActivoDTO actual = obtenerPorId(idActivo);

        // Validar máquina de estados: DADO_DE_BAJA es terminal e irreversible
        if ("DADO_DE_BAJA".equalsIgnoreCase(actual.estado())) {
            throw new TransicionEstadoActivoInvalidaException(
                "No se puede modificar el estado de un activo en estado DADO_DE_BAJA; este estado es terminal e irreversible."
            );
        }

        if (actual.estado().equalsIgnoreCase(targetEstado)) {
            return; // Idempotente
        }

        activoRepo.actualizarEstado(idActivo, propId, targetEstado);
    }

    @Override
    @Transactional
    public void darDeBaja(Long idActivo) {
        Long propId = resolvePropertyId();

        ActivoDTO actual = obtenerPorId(idActivo);

        // Eliminación estrictamente lógica: se marca DADO_DE_BAJA
        if (!"DADO_DE_BAJA".equalsIgnoreCase(actual.estado())) {
            activoRepo.actualizarEstado(idActivo, propId, "DADO_DE_BAJA");
        }
    }
}

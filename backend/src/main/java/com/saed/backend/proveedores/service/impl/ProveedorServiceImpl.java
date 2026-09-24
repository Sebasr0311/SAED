package com.saed.backend.proveedores.service.impl;

import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.proveedores.dto.ProveedorCreateDTO;
import com.saed.backend.proveedores.dto.ProveedorDTO;
import com.saed.backend.proveedores.dto.ProveedorUpdateDTO;
import com.saed.backend.proveedores.exception.ProveedorNITDuplicadoException;
import com.saed.backend.proveedores.exception.ProveedorNoEncontradoException;
import com.saed.backend.proveedores.repository.ProveedorRepository;
import com.saed.backend.proveedores.service.ProveedorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ProveedorServiceImpl implements ProveedorService {

    private static final Logger log = LoggerFactory.getLogger(ProveedorServiceImpl.class);
    private static final Set<String> ESTADOS_VALIDOS = Set.of("ACTIVO", "INACTIVO", "BLOQUEADO");

    private final ProveedorRepository proveedorRepo;
    private final PropertyRepository propertyRepo;

    public ProveedorServiceImpl(ProveedorRepository proveedorRepo, PropertyRepository propertyRepo) {
        this.proveedorRepo = proveedorRepo;
        this.propertyRepo = propertyRepo;
    }

    /**
     * Resuelve el ID de la organización autenticada de forma determinista y a prueba de IDOR.
     * Prioriza SaedContextHolder.organizationId; si es nulo pero existe propertyId,
     * resuelve la organización a partir de la propiedad activa.
     */
    private Long resolveOrganizationId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new AccessDeniedException("No se encontró contexto de seguridad en la sesión.");
        }

        if (ctx.getOrganizationId() != null) {
            return ctx.getOrganizationId();
        }

        if (ctx.getPropertyId() != null) {
            return propertyRepo.findById(ctx.getPropertyId())
                    .map(p -> p.getIdOrganizacion())
                    .orElseThrow(() -> new AccessDeniedException("No se pudo resolver la organización asociada a la propiedad activa " + ctx.getPropertyId()));
        }

        throw new AccessDeniedException("No se pudo determinar la organización activa para la operación sobre proveedores.");
    }

    private void validarCalificacion(BigDecimal calificacion) {
        if (calificacion != null) {
            if (calificacion.compareTo(new BigDecimal("1.0")) < 0 || calificacion.compareTo(new BigDecimal("5.0")) > 0) {
                throw new IllegalArgumentException("La calificación promedio debe encontrarse entre 1.0 y 5.0 (CK_PROV_CALIF).");
            }
        }
    }

    @Override
    @Transactional
    public ProveedorDTO crear(ProveedorCreateDTO dto) {
        Long orgId = resolveOrganizationId();

        // 1. Validar unicidad del NIT dentro de la organización (UQ_PROVEEDOR_NIT)
        if (proveedorRepo.existePorNit(orgId, dto.nitIdentificacion(), null)) {
            throw new ProveedorNITDuplicadoException(
                "Ya existe un proveedor registrado con el NIT o documento '" + dto.nitIdentificacion() +
                "' en esta organización."
            );
        }

        // 2. Validar rango de calificación
        validarCalificacion(dto.calificacionProm());

        log.info("[Proveedores] Creando proveedor '{}' (NIT: {}) para organización {}",
                dto.razonSocial(), dto.nitIdentificacion(), orgId);

        return proveedorRepo.crear(orgId, dto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProveedorDTO> listar(String estado, String categoria, String search) {
        Long orgId = resolveOrganizationId();
        return proveedorRepo.listar(orgId, estado, categoria, search);
    }

    @Override
    @Transactional(readOnly = true)
    public ProveedorDTO obtenerPorId(Long idProveedor) {
        Long orgId = resolveOrganizationId();

        return proveedorRepo.buscarPorId(idProveedor, orgId)
                .orElseGet(() -> {
                    // Verificación Anti-IDOR: Si existe en otra organización, rechazar explícitamente con 403
                    if (proveedorRepo.buscarPorIdDirecto(idProveedor).isPresent()) {
                        log.warn("[Anti-IDOR] Intento de acceso denegado: Proveedor {} solicitado por organización {}", idProveedor, orgId);
                        throw new AccessDeniedException("Acceso denegado: El proveedor " + idProveedor + " pertenece a otra organización.");
                    }
                    throw new ProveedorNoEncontradoException("Proveedor no encontrado con ID: " + idProveedor);
                });
    }

    @Override
    @Transactional
    public ProveedorDTO actualizar(Long idProveedor, ProveedorUpdateDTO dto) {
        Long orgId = resolveOrganizationId();

        // 1. Verificar existencia y pertenencia al tenant (Anti-IDOR)
        ProveedorDTO actual = proveedorRepo.buscarPorId(idProveedor, orgId)
                .orElseGet(() -> {
                    if (proveedorRepo.buscarPorIdDirecto(idProveedor).isPresent()) {
                        log.warn("[Anti-IDOR] Intento de modificación denegado: Proveedor {} por organización {}", idProveedor, orgId);
                        throw new AccessDeniedException("Acceso denegado: El proveedor " + idProveedor + " pertenece a otra organización.");
                    }
                    throw new ProveedorNoEncontradoException("Proveedor no encontrado con ID: " + idProveedor);
                });

        // 2. Validar unicidad del NIT excluyendo el ID actual
        if (proveedorRepo.existePorNit(orgId, dto.nitIdentificacion(), idProveedor)) {
            throw new ProveedorNITDuplicadoException(
                "Ya existe otro proveedor registrado con el NIT o documento '" + dto.nitIdentificacion() +
                "' en esta organización."
            );
        }

        // 3. Validar rango de calificación
        validarCalificacion(dto.calificacionProm());

        proveedorRepo.actualizar(idProveedor, orgId, dto);

        return proveedorRepo.buscarPorId(idProveedor, orgId)
                .orElseThrow(() -> new IllegalStateException("No se pudo recuperar el proveedor actualizado con ID: " + idProveedor));
    }

    @Override
    @Transactional
    public void actualizarEstado(Long idProveedor, String nuevoEstado) {
        if (nuevoEstado == null || !ESTADOS_VALIDOS.contains(nuevoEstado.trim().toUpperCase())) {
            throw new IllegalArgumentException("Estado no válido. Los estados permitidos son: " + ESTADOS_VALIDOS);
        }

        Long orgId = resolveOrganizationId();

        // Verificar existencia y tenant (Anti-IDOR)
        proveedorRepo.buscarPorId(idProveedor, orgId)
                .orElseGet(() -> {
                    if (proveedorRepo.buscarPorIdDirecto(idProveedor).isPresent()) {
                        log.warn("[Anti-IDOR] Intento de cambio de estado denegado: Proveedor {} por organización {}", idProveedor, orgId);
                        throw new AccessDeniedException("Acceso denegado: El proveedor " + idProveedor + " pertenece a otra organización.");
                    }
                    throw new ProveedorNoEncontradoException("Proveedor no encontrado con ID: " + idProveedor);
                });

        proveedorRepo.actualizarEstado(idProveedor, orgId, nuevoEstado.trim().toUpperCase());
    }
}

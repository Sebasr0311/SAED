package com.saed.backend.person.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.person.dto.*;
import com.saed.backend.person.repository.DependentRepository;
import com.saed.backend.person.service.DependentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;

@Service
@Transactional
public class DependentServiceImpl implements DependentService {

    private final DependentRepository dependentRepository;

    public DependentServiceImpl(DependentRepository dependentRepository) {
        this.dependentRepository = dependentRepository;
    }

    // --- Scope (F08-04) ---
    private static final java.util.Set<String> ESPECIES = java.util.Set.of("PERRO", "GATO", "AVE", "ROEDOR", "PECES", "OTRO");
    private static final java.util.Set<String> TIPOS_VEHICULO =
        java.util.Set.of("AUTOMOVIL", "MOTOCICLETA", "BICICLETA", "ELECTRICO", "DE_CARGA", "OTRO");
    private static final java.util.regex.Pattern PLACA = java.util.regex.Pattern.compile("^[A-Z0-9-]{3,10}$");
    private static final java.util.regex.Pattern DATA_IMAGE =
        java.util.regex.Pattern.compile("^data:(image/(png|jpeg|jpg|webp)|application/pdf);base64,[A-Za-z0-9+/=\\s]+$");
    /** Maximum encoded size of an inline (data:) upload: about 2 MB of binary content. */
    static final int MAX_INLINE_MEDIA_CHARS = 2_800_000;
    private static final int MAX_URL_LENGTH = 1000;

    private static com.saed.backend.context.SaedContext requireContext() {
        com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Missing tenant context");
        }
        if ("SUPERADMIN".equals(ctx.getRoleCode()) || "GLOBAL".equals(ctx.getRoleScope())) {
            throw new AccessDeniedException("Platform administrators do not manage resident assets");
        }
        return ctx;
    }

    private static boolean isResident(com.saed.backend.context.SaedContext ctx) {
        return "RESIDENTE".equals(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode())
            || "UNIDAD".equals(ctx.getRoleScope());
    }

    /** The caller may manage assets of this unit: their own unit (residents) or a unit of their property (staff). */
    private void requireUnitAccess(Long unidadId) {
        com.saed.backend.context.SaedContext ctx = requireContext();
        if (unidadId == null) {
            throw new IllegalArgumentException("La unidad es obligatoria");
        }
        if (isResident(ctx)) {
            if (ctx.getUnitId() == null || !ctx.getUnitId().equals(unidadId)) {
                throw new AccessDeniedException("Solo puede gestionar registros de su propia unidad");
            }
            return;
        }
        if (ctx.getPropertyId() == null || !dependentRepository.isUnitInProperty(unidadId, ctx.getPropertyId())) {
            throw new AccessDeniedException("La unidad no pertenece a la propiedad asignada");
        }
    }

    /** Reads keep not-found and out-of-scope indistinguishable. */
    private void requireReadable(Long unidadId) {
        try {
            requireUnitAccess(unidadId);
        } catch (AccessDeniedException e) {
            if (com.saed.backend.context.SaedContextHolder.getContext() == null) {
                throw e;
            }
            com.saed.backend.context.SaedContext ctx = com.saed.backend.context.SaedContextHolder.getContext();
            if ("SUPERADMIN".equals(ctx.getRoleCode()) || "GLOBAL".equals(ctx.getRoleScope())) {
                throw e;
            }
            throw new NoSuchElementException("Registro no encontrado");
        }
    }

    private void requireOwnerOfUnit(Long personaId, Long unidadId) {
        if (personaId == null || !dependentRepository.isPersonOfUnit(personaId, unidadId)) {
            throw new AccessDeniedException("El responsable debe ser residente o propietario de la unidad");
        }
    }

    private static void validateMedia(String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String v = value.trim();
        if (v.startsWith("data:")) {
            if (v.length() > MAX_INLINE_MEDIA_CHARS) {
                throw new IllegalArgumentException("El archivo de " + field + " supera el tamaño máximo de 2 MB");
            }
            if (!DATA_IMAGE.matcher(v).matches()) {
                throw new IllegalArgumentException("Formato no permitido para " + field + " (PNG, JPEG, WEBP o PDF)");
            }
            return;
        }
        if (v.length() > MAX_URL_LENGTH || !(v.startsWith("https://") || v.startsWith("http://") || v.startsWith("/uploads/"))) {
            throw new IllegalArgumentException("Enlace no permitido para " + field);
        }
    }

    private static void validatePet(MascotaRequestDTO r) {
        if (r.especie() == null || !ESPECIES.contains(r.especie().trim().toUpperCase())) {
            throw new IllegalArgumentException("Especie no válida; use " + ESPECIES);
        }
        if (r.pesoKg() != null && r.pesoKg() <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor a cero");
        }
        validateMedia("foto", r.fotoUrl());
        validateMedia("carnet de vacunación", r.carnetVacunacionUrl());
        validateMedia("póliza", r.polizaResponsabilidadUrl());
    }

    private static void validateVehicle(VehiculoRequestDTO r) {
        String placa = r.placa() != null ? r.placa().trim().toUpperCase() : "";
        if (!PLACA.matcher(placa).matches()) {
            throw new IllegalArgumentException("Placa no válida (3 a 10 letras, números o guiones)");
        }
        if (r.tipoVehiculo() == null || !TIPOS_VEHICULO.contains(r.tipoVehiculo().trim().toUpperCase())) {
            throw new IllegalArgumentException("Tipo de vehículo no válido; use " + TIPOS_VEHICULO);
        }
    }

    // --- Mascotas ---
    @Override
    @Auditable(action = "CREATE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public MascotaDTO createMascota(MascotaRequestDTO request) {
        requireUnitAccess(request.unidadId());
        requireOwnerOfUnit(request.responsableId(), request.unidadId());
        validatePet(request);
        return dependentRepository.createMascota(request);
    }

    @Override
    @Transactional(readOnly = true)
    public MascotaDTO getMascotaById(Long id) {
        MascotaDTO mascota = dependentRepository.getMascotaById(id)
                .orElseThrow(() -> new NoSuchElementException("Mascota no encontrada"));
        requireReadable(mascota.unidadId());
        return mascota;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MascotaDTO> getMascotasByUnidad(Long unidadId) {
        requireUnitAccess(unidadId);
        return dependentRepository.getMascotasByUnidad(unidadId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public MascotaDTO updateMascota(Long id, MascotaRequestDTO request) {
        MascotaDTO existing = getMascotaById(id);
        if (request.unidadId() != null && !request.unidadId().equals(existing.unidadId())) {
            throw new AccessDeniedException("No puede transferir una mascota a otra unidad");
        }
        requireOwnerOfUnit(request.responsableId(), existing.unidadId());
        validatePet(request);
        return dependentRepository.updateMascota(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "MASCOTA", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteMascota(Long id) {
        getMascotaById(id);
        dependentRepository.deleteMascota(id);
    }

    // --- Vehiculos ---
    @Override
    @Auditable(action = "CREATE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VehiculoDTO createVehiculo(VehiculoRequestDTO request) {
        requireUnitAccess(request.unidadId());
        requireOwnerOfUnit(request.personaId(), request.unidadId());
        validateVehicle(request);
        return dependentRepository.createVehiculo(request);
    }

    @Override
    @Transactional(readOnly = true)
    public VehiculoDTO getVehiculoById(Long id) {
        VehiculoDTO vehiculo = dependentRepository.getVehiculoById(id)
                .orElseThrow(() -> new NoSuchElementException("Vehiculo no encontrado"));
        requireReadable(vehiculo.unidadId());
        return vehiculo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehiculoDTO> getVehiculosByUnidad(Long unidadId) {
        requireUnitAccess(unidadId);
        return dependentRepository.getVehiculosByUnidad(unidadId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VehiculoDTO updateVehiculo(Long id, VehiculoRequestDTO request) {
        VehiculoDTO existing = getVehiculoById(id);
        if (request.unidadId() != null && !request.unidadId().equals(existing.unidadId())) {
            throw new AccessDeniedException("No puede transferir un vehículo a otra unidad");
        }
        requireOwnerOfUnit(request.personaId(), existing.unidadId());
        validateVehicle(request);
        return dependentRepository.updateVehiculo(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "VEHICULO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteVehiculo(Long id) {
        getVehiculoById(id);
        dependentRepository.deleteVehiculo(id);
    }

    // --- Tutores (F5-H08: guardian rights are scoped to the dependent) ---

    /** The dependent must live in (or own) a unit the caller may manage. */
    private void requireDependentAccess(Long menorId) {
        com.saed.backend.context.SaedContext ctx = requireContext();
        if (menorId == null) {
            throw new IllegalArgumentException("La persona dependiente es obligatoria");
        }
        List<Long> units = dependentRepository.findActiveUnitIdsOfPersona(menorId);
        boolean allowed;
        if (isResident(ctx)) {
            allowed = ctx.getUnitId() != null && units.contains(ctx.getUnitId());
        } else {
            allowed = ctx.getPropertyId() != null
                    && units.stream().anyMatch(u -> dependentRepository.isUnitInProperty(u, ctx.getPropertyId()));
        }
        if (!allowed) {
            throw new AccessDeniedException("La persona dependiente no pertenece a su alcance");
        }
    }

    /** Reads and changes of an existing guardian: out of scope looks like not found. */
    private TutorDTO requireReadableTutor(Long id) {
        TutorDTO tutor = dependentRepository.getTutorById(id)
                .orElseThrow(() -> new NoSuchElementException("Tutor no encontrado"));
        try {
            requireDependentAccess(tutor.personaMenorId());
        } catch (AccessDeniedException e) {
            requireContext();
            throw new NoSuchElementException("Tutor no encontrado");
        }
        return tutor;
    }

    @Override
    @Auditable(action = "CREATE", resource = "TUTOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public TutorDTO createTutor(TutorRequestDTO request) {
        requireDependentAccess(request.personaMenorId());
        return dependentRepository.createTutor(request);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorDTO getTutorById(Long id) {
        return requireReadableTutor(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TutorDTO> getTutoresByMenor(Long menorId) {
        try {
            requireDependentAccess(menorId);
        } catch (AccessDeniedException e) {
            requireContext();
            throw new NoSuchElementException("Persona no encontrada");
        }
        return dependentRepository.getTutoresByMenor(menorId);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "TUTOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public TutorDTO updateTutor(Long id, TutorRequestDTO request) {
        requireReadableTutor(id);
        requireDependentAccess(request.personaMenorId());
        return dependentRepository.updateTutor(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "TUTOR", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteTutor(Long id) {
        requireReadableTutor(id);
        dependentRepository.deleteTutor(id);
    }

    // --- Visitantes ---
    @Override
    @Auditable(action = "CREATE", resource = "VISITANTE", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VisitanteDTO createVisitante(VisitanteRequestDTO request) {
        return dependentRepository.createVisitante(request);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitanteDTO getVisitanteById(Long id) {
        return dependentRepository.getVisitanteById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Visitante no encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public VisitanteDTO getVisitanteByPersona(Long personaId) {
        return dependentRepository.getVisitanteByPersona(personaId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Visitante no encontrado"));
    }

    @Override
    @Auditable(action = "UPDATE", resource = "VISITANTE", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public VisitanteDTO updateVisitante(Long id, VisitanteRequestDTO request) {
        return dependentRepository.updateVisitante(id, request);
    }

    @Override
    @Auditable(action = "DELETE", resource = "VISITANTE", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public void deleteVisitante(Long id) {
        dependentRepository.deleteVisitante(id);
    }
}

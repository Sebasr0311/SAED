package com.saed.backend.emergencias.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.emergencias.dto.*;
import com.saed.backend.emergencias.repository.ContactoEmergenciaRepository;
import com.saed.backend.emergencias.repository.PlanEmergenciaRepository;
import com.saed.backend.emergencias.service.EmergenciasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EmergenciasServiceImpl implements EmergenciasService {

    private final PlanEmergenciaRepository planRepository;
    private final ContactoEmergenciaRepository contactoRepository;

    public EmergenciasServiceImpl(PlanEmergenciaRepository planRepository,
                                  ContactoEmergenciaRepository contactoRepository) {
        this.planRepository = planRepository;
        this.contactoRepository = contactoRepository;
    }

    private Long getCurrentPropertyId() {
        Long propId = SaedContextHolder.getContext().getPropertyId();
        if (propId == null) {
            throw new IllegalStateException("No hay una copropiedad activa en el contexto de seguridad.");
        }
        return propId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanEmergenciaDTO> getAllPlanes() {
        return planRepository.findAllByPropiedad(getCurrentPropertyId());
    }

    @Override
    @Transactional(readOnly = true)
    public PlanEmergenciaDTO getPlanById(Long id) {
        return planRepository.findByIdAndPropiedad(id, getCurrentPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Plan de emergencia no encontrado con ID: " + id));
    }

    @Override
    public Long createPlan(PlanEmergenciaRequestDTO dto) {
        return planRepository.insert(getCurrentPropertyId(), dto);
    }

    @Override
    public void updatePlan(Long id, PlanEmergenciaRequestDTO dto) {
        Long propId = getCurrentPropertyId();
        planRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Plan de emergencia no encontrado con ID: " + id));
        planRepository.update(id, propId, dto);
    }

    @Override
    public void deletePlan(Long id) {
        Long propId = getCurrentPropertyId();
        planRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Plan de emergencia no encontrado con ID: " + id));
        planRepository.delete(id, propId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactoEmergenciaDTO> getAllContactos() {
        return contactoRepository.findAllByPropiedad(getCurrentPropertyId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactoEmergenciaDTO> getContactosMinuta() {
        return contactoRepository.findPrioritariosByPropiedad(getCurrentPropertyId());
    }

    @Override
    @Transactional(readOnly = true)
    public ContactoEmergenciaDTO getContactoById(Long id) {
        return contactoRepository.findByIdAndPropiedad(id, getCurrentPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Contacto de emergencia no encontrado con ID: " + id));
    }

    @Override
    public Long createContacto(ContactoEmergenciaRequestDTO dto) {
        return contactoRepository.insert(getCurrentPropertyId(), dto);
    }

    @Override
    public void updateContacto(Long id, ContactoEmergenciaRequestDTO dto) {
        Long propId = getCurrentPropertyId();
        contactoRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Contacto de emergencia no encontrado con ID: " + id));
        contactoRepository.update(id, propId, dto);
    }

    @Override
    public void deleteContacto(Long id) {
        Long propId = getCurrentPropertyId();
        contactoRepository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Contacto de emergencia no encontrado con ID: " + id));
        contactoRepository.delete(id, propId);
    }

    @Override
    @Transactional(readOnly = true)
    public EmergenciasSummaryDTO getResumen() {
        Long propId = getCurrentPropertyId();
        int totalPlanes = planRepository.countTotalByPropiedad(propId);
        int planesActivos = planRepository.countActivosByPropiedad(propId);
        int totalContactos = contactoRepository.countTotalByPropiedad(propId);
        int contactosPrioritarios = contactoRepository.countPrioritariosByPropiedad(propId);
        return new EmergenciasSummaryDTO(totalPlanes, planesActivos, totalContactos, contactosPrioritarios);
    }
}

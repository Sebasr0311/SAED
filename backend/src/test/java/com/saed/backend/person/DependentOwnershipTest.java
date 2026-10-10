package com.saed.backend.person;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.MascotaDTO;
import com.saed.backend.person.dto.MascotaRequestDTO;
import com.saed.backend.person.dto.VehiculoDTO;
import com.saed.backend.person.dto.VehiculoRequestDTO;
import com.saed.backend.person.repository.DependentRepository;
import com.saed.backend.person.service.impl.DependentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F08-04: pets and vehicles are managed only for the caller's own unit with a resident of that unit as owner. */
class DependentOwnershipTest {

    private static final long PROP = 70L;
    private static final long UNIT = 701L;
    private static final long OTHER_UNIT = 801L;
    private static final long OWNER = 500L;
    private static final long STRANGER = 900L;

    private DependentRepository repo;
    private DependentServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(DependentRepository.class);
        service = new DependentServiceImpl(repo);
        when(repo.isPersonOfUnit(OWNER, UNIT)).thenReturn(true);
        when(repo.isUnitInProperty(UNIT, PROP)).thenReturn(true);
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void resident(Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(40L).organizationId(1L).propertyId(PROP)
            .unitId(unit).roleCode("RESIDENTE").roleScope("UNIDAD").build());
    }

    private static void admin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(1L).propertyId(PROP)
            .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    private static MascotaRequestDTO pet(long unit, long owner, String foto) {
        return new MascotaRequestDTO(unit, owner, "Toby", "PERRO", null, null, "M", null, 12.0, null,
            "N", null, null, foto, "ACTIVA");
    }

    private static VehiculoRequestDTO car(long unit, long owner, String placa) {
        return new VehiculoRequestDTO(owner, unit, placa, "AUTOMOVIL", null, null, null, null, "ACTIVO");
    }

    @Test
    void residentRegistersAPetOfTheirUnitWithAResidentOwner() {
        resident(UNIT);
        service.createMascota(pet(UNIT, OWNER, "https://cdn.example.com/toby.jpg"));
        verify(repo).createMascota(any());
    }

    @Test
    void forgedUnitIsRejected() {
        resident(UNIT);
        assertThrows(AccessDeniedException.class, () -> service.createMascota(pet(OTHER_UNIT, OWNER, null)));
        assertThrows(AccessDeniedException.class, () -> service.createVehiculo(car(OTHER_UNIT, OWNER, "ABC123")));
        verify(repo, never()).createMascota(any());
        verify(repo, never()).createVehiculo(any());
    }

    @Test
    void residentWithoutUnitContextIsDenied() {
        resident(null);
        assertThrows(AccessDeniedException.class, () -> service.createMascota(pet(UNIT, OWNER, null)));
        assertThrows(AccessDeniedException.class, () -> service.getVehiculosByUnidad(UNIT));
    }

    @Test
    void forgedOwnerIsRejected() {
        resident(UNIT);
        assertThrows(AccessDeniedException.class, () -> service.createMascota(pet(UNIT, STRANGER, null)));
        assertThrows(AccessDeniedException.class, () -> service.createVehiculo(car(UNIT, STRANGER, "ABC123")));
    }

    @Test
    void adminIsLimitedToUnitsOfTheirProperty() {
        admin();
        assertThrows(AccessDeniedException.class, () -> service.createVehiculo(car(OTHER_UNIT, OWNER, "ABC123")));
        assertThrows(AccessDeniedException.class, () -> service.getMascotasByUnidad(OTHER_UNIT));
        service.createVehiculo(car(UNIT, OWNER, "ABC123"));
        verify(repo).createVehiculo(any());
    }

    @Test
    void adminCannotReadAPetOfAnotherProperty() {
        admin();
        when(repo.getMascotaById(3L)).thenReturn(Optional.of(new MascotaDTO(3L, OTHER_UNIT, OWNER, "Toby", "PERRO",
            null, null, "M", null, null, null, "N", null, null, null, "ACTIVA")));
        assertThrows(NoSuchElementException.class, () -> service.getMascotaById(3L));
    }

    @Test
    void unsafeOrOversizedMediaIsRejected() {
        resident(UNIT);
        assertThrows(IllegalArgumentException.class, () -> service.createMascota(pet(UNIT, OWNER, "javascript:alert(1)")));
        assertThrows(IllegalArgumentException.class, () -> service.createMascota(pet(UNIT, OWNER, "data:text/html;base64,PGI+")));
        String huge = "data:image/png;base64," + "A".repeat(3 * 1024 * 1024);
        assertThrows(IllegalArgumentException.class, () -> service.createMascota(pet(UNIT, OWNER, huge)));
        verify(repo, never()).createMascota(any());
    }

    @Test
    void requiredMetadataIsValidated() {
        resident(UNIT);
        MascotaRequestDTO badSpecies = new MascotaRequestDTO(UNIT, OWNER, "Toby", "DRAGON", null, null, "M", null,
            null, null, "N", null, null, null, "ACTIVA");
        assertThrows(IllegalArgumentException.class, () -> service.createMascota(badSpecies));
        assertThrows(IllegalArgumentException.class, () -> service.createVehiculo(car(UNIT, OWNER, "A B<C>")));
        VehiculoRequestDTO badType = new VehiculoRequestDTO(OWNER, UNIT, "ABC123", "TANQUE", null, null, null, null, "ACTIVO");
        assertThrows(IllegalArgumentException.class, () -> service.createVehiculo(badType));
    }

    @Test
    void residentCannotDeleteAVehicleOfAnotherUnit() {
        resident(UNIT);
        when(repo.getVehiculoById(4L)).thenReturn(Optional.of(new VehiculoDTO(4L, OWNER, OTHER_UNIT, "ABC123",
            "AUTOMOVIL", null, null, null, null, "ACTIVO", null)));
        assertThrows(NoSuchElementException.class, () -> service.deleteVehiculo(4L));
        verify(repo, never()).deleteVehiculo(any());
    }

    @Test
    void superadminDoesNotManageResidentAssets() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.getMascotasByUnidad(UNIT));
    }
}

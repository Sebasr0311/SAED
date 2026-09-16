package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.BlockDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;
import com.saed.backend.authorization.dto.BlockTreeDTO;
import com.saed.backend.authorization.repository.BlockRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.PlanLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private PlanLimitService planLimitService;

    private BlockServiceImpl blockService;

    @BeforeEach
    void setUp() {
        blockService = new BlockServiceImpl(blockRepository, planLimitService);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void create_asSuperAdmin_shouldSucceed() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockRequestDTO req = new BlockRequestDTO();
        req.setTipo("TORRE");
        req.setCodigo("T1");
        req.setNombre("Torre 1");
        req.setOrden(1);

        when(blockRepository.create(any(BlockRequestDTO.class))).thenReturn(100L);

        Long createdId = blockService.create(10L, req);
        assertEquals(100L, createdId);
        verify(blockRepository).create(any(BlockRequestDTO.class));
    }

    @Test
    void create_crossPropertySpoofing_shouldThrowAccessDenied() {
        // Admin for property 10 attempts to create block in property 20
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .propertyId(10L).build());

        BlockRequestDTO req = new BlockRequestDTO();
        req.setTipo("TORRE");
        req.setCodigo("T1");
        req.setNombre("Torre 1");

        assertThrows(AccessDeniedException.class, () -> blockService.create(20L, req));
        verify(blockRepository, never()).create(any());
    }

    @Test
    void create_withInvalidTipo_shouldThrowIllegalArgumentException() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockRequestDTO req = new BlockRequestDTO();
        req.setTipo("INVALID_TYPE");
        req.setCodigo("T1");
        req.setNombre("Torre 1");

        assertThrows(IllegalArgumentException.class, () -> blockService.create(10L, req));
    }

    @Test
    void update_cycleDetection_shouldThrowIllegalArgumentException() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockDTO existing = new BlockDTO();
        existing.setId(100L);
        existing.setIdPropiedad(10L);
        when(blockRepository.findById(100L)).thenReturn(Optional.of(existing));

        // Block 100 trying to set parent as Block 100
        BlockRequestDTO req = new BlockRequestDTO();
        req.setTipo("TORRE");
        req.setCodigo("T1");
        req.setNombre("Torre 1");
        req.setIdBloquePadre(100L);

        assertThrows(IllegalArgumentException.class, () -> blockService.update(10L, 100L, req));
    }

    @Test
    void delete_whenBlockHasChildBlocks_shouldThrowIllegalStateException() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockDTO existing = new BlockDTO();
        existing.setId(100L);
        existing.setIdPropiedad(10L);
        when(blockRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(blockRepository.countChildren(100L)).thenReturn(2);

        assertThrows(IllegalStateException.class, () -> blockService.delete(10L, 100L));
        verify(blockRepository, never()).delete(any());
    }

    @Test
    void delete_whenBlockHasUnitsAssigned_shouldThrowIllegalStateException() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockDTO existing = new BlockDTO();
        existing.setId(100L);
        existing.setIdPropiedad(10L);
        when(blockRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(blockRepository.countChildren(100L)).thenReturn(0);
        when(blockRepository.countUnits(100L)).thenReturn(5);

        assertThrows(IllegalStateException.class, () -> blockService.delete(10L, 100L));
        verify(blockRepository, never()).delete(any());
    }

    @Test
    void delete_whenBlockHasNoChildrenOrUnits_shouldSucceed() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockDTO existing = new BlockDTO();
        existing.setId(100L);
        existing.setIdPropiedad(10L);
        when(blockRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(blockRepository.countChildren(100L)).thenReturn(0);
        when(blockRepository.countUnits(100L)).thenReturn(0);

        blockService.delete(10L, 100L);
        verify(blockRepository).delete(100L);
    }

    @Test
    void findTree_shouldBuildHierarchyCorrectly() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        BlockDTO root = new BlockDTO();
        root.setId(1L);
        root.setIdPropiedad(10L);
        root.setNombre("Torre 1");
        root.setTipo("TORRE");
        root.setCodigo("T1");
        root.setIdBloquePadre(null);

        BlockDTO child = new BlockDTO();
        child.setId(2L);
        child.setIdPropiedad(10L);
        child.setNombre("Piso 1");
        child.setTipo("PISO");
        child.setCodigo("P1");
        child.setIdBloquePadre(1L);

        when(blockRepository.findByPropertyId(10L)).thenReturn(List.of(root, child));

        List<BlockTreeDTO> tree = blockService.findTreeByPropertyId(10L);
        assertEquals(1, tree.size());
        assertEquals("Torre 1", tree.get(0).getNombre());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("Piso 1", tree.get(0).getChildren().get(0).getNombre());
    }
}

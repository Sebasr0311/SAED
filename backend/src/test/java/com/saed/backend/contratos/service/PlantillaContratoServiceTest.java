package com.saed.backend.contratos.service;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import com.saed.backend.contratos.repository.PlantillaContratoRepository;
import com.saed.backend.contratos.service.impl.PlantillaContratoServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlantillaContratoServiceTest {

    @Mock
    private PlantillaContratoRepository plantillaRepository;

    @Mock
    private PropertyRepository propertyRepository;

    private PlantillaContratoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlantillaContratoServiceImpl(plantillaRepository, propertyRepository);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void crear_conOrganizacionValida_generaIdYVersionInicial() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(10L).organizationId(1L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        PlantillaContratoRequestDTO request = new PlantillaContratoRequestDTO();
        request.setCodigo("CONTRATO_RESIDENCIAL");
        request.setNombre("Contrato Residencial Estándar");
        request.setTipoContrato("INICIAL");
        request.setContenidoHtml("<h1>Contrato ${propiedad.nombre}</h1><p>Residente: ${residente.nombreCompleto}</p>");

        when(plantillaRepository.getMaxVersion(1L, "CONTRATO_RESIDENCIAL")).thenReturn(0);
        when(plantillaRepository.create(any(), eq(1L), eq(10L))).thenReturn(100L);

        PlantillaContratoDTO createdDto = new PlantillaContratoDTO();
        createdDto.setIdPlantilla(100L);
        createdDto.setIdOrganizacion(1L);
        createdDto.setCodigo("CONTRATO_RESIDENCIAL");
        createdDto.setNombre("Contrato Residencial Estándar");
        createdDto.setVersion(1);
        createdDto.setEstado("ACTIVA");

        when(plantillaRepository.findById(100L)).thenReturn(Optional.of(createdDto));

        PlantillaContratoDTO result = service.crear(request);

        assertNotNull(result);
        assertEquals(100L, result.getIdPlantilla());
        assertEquals(1, request.getVersion());
        verify(plantillaRepository).create(request, 1L, 10L);
    }

    @Test
    void crearNuevaVersion_marcaAnteriorComoHistoricaEIncrementaVersion() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(10L).organizationId(1L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        PlantillaContratoDTO anterior = new PlantillaContratoDTO();
        anterior.setIdPlantilla(50L);
        anterior.setIdOrganizacion(1L);
        anterior.setCodigo("ARRIENDO");
        anterior.setNombre("Arriendo V1");
        anterior.setTipoContrato("INICIAL");
        anterior.setContenidoHtml("Versión 1");
        anterior.setVersion(1);
        anterior.setEstado("ACTIVA");

        when(plantillaRepository.findById(50L)).thenReturn(Optional.of(anterior));

        PlantillaContratoRequestDTO newReq = new PlantillaContratoRequestDTO();
        newReq.setNombre("Arriendo V2");
        newReq.setContenidoHtml("Versión 2 mejorada");

        when(plantillaRepository.create(any(), eq(1L), eq(10L))).thenReturn(51L);

        PlantillaContratoDTO nuevaVersion = new PlantillaContratoDTO();
        nuevaVersion.setIdPlantilla(51L);
        nuevaVersion.setIdOrganizacion(1L);
        nuevaVersion.setCodigo("ARRIENDO");
        nuevaVersion.setNombre("Arriendo V2");
        nuevaVersion.setVersion(2);
        nuevaVersion.setEstado("ACTIVA");

        when(plantillaRepository.findById(51L)).thenReturn(Optional.of(nuevaVersion));

        PlantillaContratoDTO result = service.crearNuevaVersion(50L, newReq);

        assertNotNull(result);
        assertEquals(51L, result.getIdPlantilla());
        assertEquals(2, result.getVersion());
        verify(plantillaRepository).updateStatus(50L, "HISTORICA");
    }

    @Test
    void actualizar_plantillaHistorica_arrojaIllegalStateException() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(10L).organizationId(1L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        PlantillaContratoDTO historica = new PlantillaContratoDTO();
        historica.setIdPlantilla(20L);
        historica.setIdOrganizacion(1L);
        historica.setEstado("HISTORICA");

        when(plantillaRepository.findById(20L)).thenReturn(Optional.of(historica));

        PlantillaContratoRequestDTO req = new PlantillaContratoRequestDTO();
        req.setNombre("Modificación prohibida");

        assertThrows(IllegalStateException.class, () -> service.actualizar(20L, req));
        verify(plantillaRepository, never()).update(any(), any());
    }

    @Test
    void renderizarPlantilla_reemplazaVariablesDinamicasCorrectamente() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(10L).organizationId(1L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        PlantillaContratoDTO plantilla = new PlantillaContratoDTO();
        plantilla.setIdPlantilla(1L);
        plantilla.setIdOrganizacion(1L);
        plantilla.setContenidoHtml("Copropiedad: ${propiedad.nombre} | Residente: ${residente.nombreCompleto} | Apto: ${unidad.identificador}");

        when(plantillaRepository.findById(1L)).thenReturn(Optional.of(plantilla));

        Map<String, Object> vars = Map.of(
                "propiedad.nombre", "Torres del Parque",
                "residente.nombreCompleto", "Carlos Gómez",
                "unidad.identificador", "Apto 302"
        );

        String render = service.renderizarPlantilla(1L, vars);

        assertEquals("Copropiedad: Torres del Parque | Residente: Carlos Gómez | Apto: Apto 302", render);
    }

    @Test
    void listarActivasParaPropiedad_resuelveOrganizacionDesdePropiedad() {
        // Simular que el usuario tiene propertyId en contexto pero no organizationId
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(20L).propertyId(77L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());

        PropertyDTO prop = new PropertyDTO();
        prop.setId(77L);
        prop.setIdOrganizacion(5L);
        when(propertyRepository.findById(77L)).thenReturn(Optional.of(prop));

        PlantillaContratoDTO p1 = new PlantillaContratoDTO();
        p1.setIdPlantilla(1L);
        p1.setNombre("Plantilla 1");
        when(plantillaRepository.findActivasByOrganizacionId(5L)).thenReturn(List.of(p1));

        List<PlantillaContratoDTO> list = service.listarActivasParaPropiedad();

        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getIdPlantilla());
        verify(propertyRepository).findById(77L);
        verify(plantillaRepository).findActivasByOrganizacionId(5L);
    }

    @Test
    void cambiarEstado_validaEstadosPermitidos() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(10L).organizationId(1L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());

        PlantillaContratoDTO p = new PlantillaContratoDTO();
        p.setIdPlantilla(99L);
        p.setIdOrganizacion(1L);
        when(plantillaRepository.findById(99L)).thenReturn(Optional.of(p));

        service.cambiarEstado(99L, "BORRADOR");
        verify(plantillaRepository).updateStatus(99L, "BORRADOR");

        assertThrows(IllegalArgumentException.class, () -> service.cambiarEstado(99L, "ESTADO_INVALIDO"));
    }
}

package com.saed.backend.documentos.service.impl;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.repository.DocumentoRepository;
import com.saed.backend.platform.service.StorageQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Document integrity (audit F10-05, F10-14; plan F10-03): no client-chosen file paths, files
 * partitioned by organization, cross-tenant files denied, visibility enforced, versions preserved
 * and orphan files removed when the metadata write fails. Pure Mockito, no database or disk.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentoIntegrityServiceTest {

    private static final Long ORG_ID = 40L;
    private static final Long PROPERTY_ID = 30L;
    private static final Long USER_ID = 60L;
    private static final Long DOC_ID = 7L;
    private static final String OWN_PATH = "documentos/org-40/uuid_reglamento.pdf";

    @Mock
    private DocumentoRepository repository;
    @Mock
    private StorageQuotaService storageQuotaService;
    @Mock
    private FileStorageService fileStorageService;

    private DocumentoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentoServiceImpl(repository, storageQuotaService, fileStorageService);
        when(fileStorageService.store(any(), anyString(), any()))
                .thenReturn(new FileStorageService.StoredFile(OWN_PATH, "reglamento.pdf", "application/pdf", 10L, "abc"));
        when(repository.createDocumento(any(), any(), any(), any())).thenReturn(DOC_ID);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void jsonCreateRejectsClientSuppliedFilePath() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        DocumentoDTO request = metadata();
        request.setArchivoUrl("gastos/uuid_other_tenant.pdf");

        assertThrows(IllegalArgumentException.class, () -> service.uploadDocumento(request));

        verify(repository, never()).createDocumento(any(), any(), any(), any());
        verify(repository, never()).addVersion(any(), any(), any());
    }

    @Test
    void jsonCreateUsesSessionTenantNotBodyIds() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        DocumentoDTO request = metadata();
        request.setIdOrganizacion(999L);
        request.setIdPropiedad(998L);

        service.uploadDocumento(request);

        verify(repository).createDocumento(any(), eq(ORG_ID), eq(PROPERTY_ID), eq(USER_ID));
        verify(repository, never()).addVersion(any(), any(), any());
    }

    @Test
    void uploadedFileIsStoredInTheOrganizationPartition() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);

        service.uploadDocumentoMultipart(pdf(), "Reglamento", "REGLAMENTO_INTERNO", null, "S", null);

        verify(fileStorageService).store(any(), eq("documentos/org-40"), eq(ORG_ID));
    }

    @Test
    void failedMetadataWriteDeletesTheStoredFile() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        when(repository.createDocumento(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("CK_DOCUMENTOS_CATEGORIA"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadDocumentoMultipart(pdf(), "Reglamento", "REGLAMENTO_INTERNO", null, "S", null));

        verify(fileStorageService).delete(OWN_PATH);
    }

    @Test
    void failedVersionWriteDeletesTheStoredFile() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        givenDocument(true, "S", "ACTIVO", OWN_PATH);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("UQ_VERSION"))
                .when(repository).addVersion(eq(DOC_ID), any(), any());

        assertThrows(DataIntegrityViolationException.class,
                () -> service.addVersionMultipart(DOC_ID, pdf(), "v2"));

        verify(fileStorageService).delete(OWN_PATH);
    }

    @Test
    void newVersionKeepsPreviousVersionsAndFiles() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        givenDocument(true, "S", "ACTIVO", "documentos/org-40/uuid_v1.pdf");

        service.addVersionMultipart(DOC_ID, pdf(), "v2");

        ArgumentCaptor<DocumentoDTO> version = ArgumentCaptor.forClass(DocumentoDTO.class);
        verify(repository).addVersion(eq(DOC_ID), version.capture(), eq(USER_ID));
        assertEquals(OWN_PATH, version.getValue().getArchivoUrl());
        verify(fileStorageService, never()).delete(anyString());
        verify(repository, never()).deleteDocumento(anyLong(), any());
    }

    @Test
    void fileOfAnotherOrganizationPartitionIsNotServed() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        givenDocument(true, "S", "ACTIVO", "documentos/org-41/uuid_foreign.pdf");

        assertThrows(NoSuchElementException.class, () -> service.downloadDocumento(DOC_ID));

        verify(fileStorageService, never()).loadAsResource(anyString());
    }

    @Test
    void fileOutsideTheDocumentsAreaIsNotServed() {
        loginAs("ADMIN_PROPIEDAD", ORG_ID, PROPERTY_ID);
        givenDocument(true, "S", "ACTIVO", "gastos/uuid_receipt.pdf");

        assertThrows(NoSuchElementException.class, () -> service.downloadDocumento(DOC_ID));

        verify(fileStorageService, never()).loadAsResource(anyString());
    }

    @Test
    void residentCannotReadPrivateDocument() {
        loginAs("RESIDENTE", ORG_ID, PROPERTY_ID);
        givenDocument(true, "N", "ACTIVO", OWN_PATH);

        assertThrows(AccessDeniedException.class, () -> service.getDocumentoById(DOC_ID));
    }

    @Test
    void residentCannotReadPublicDocumentThatIsNotActive() {
        loginAs("RESIDENTE", ORG_ID, PROPERTY_ID);
        givenDocument(true, "S", "INACTIVO", OWN_PATH);

        assertThrows(AccessDeniedException.class, () -> service.getDocumentoById(DOC_ID));
    }

    @Test
    void residentResponseDoesNotExposeTheInternalFilePath() {
        loginAs("RESIDENTE", ORG_ID, PROPERTY_ID);
        givenDocument(true, "S", "ACTIVO", OWN_PATH);

        assertNull(service.getDocumentoById(DOC_ID).getArchivoUrl());
    }

    @Test
    void superadminCannotReadOperationalDocuments() {
        loginAs("SUPERADMIN", null, null);
        givenDocument(true, "S", "ACTIVO", OWN_PATH);

        assertThrows(AccessDeniedException.class, () -> service.getDocumentoById(DOC_ID));
    }

    @Test
    void administratorOfAnotherOrganizationCannotReadDocument() {
        loginAs("ADMIN_ORGANIZACION", ORG_ID + 1, null);
        givenDocument(true, "S", "ACTIVO", OWN_PATH);

        assertThrows(AccessDeniedException.class, () -> service.getDocumentoById(DOC_ID));
    }

    private void loginAs(String role, Long orgId, Long propertyId) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ID)
                .organizationId(orgId)
                .propertyId(propertyId)
                .roleCode(role)
                .build());
    }

    private void givenDocument(boolean exists, String publicForResidents, String state, String path) {
        DocumentoDTO doc = new DocumentoDTO();
        doc.setIdDocumento(DOC_ID);
        doc.setIdOrganizacion(ORG_ID);
        doc.setIdPropiedad(PROPERTY_ID);
        doc.setEsPublicoResidentes(publicForResidents);
        doc.setEstado(state);
        doc.setArchivoUrl(path);
        doc.setNumeroVersion(1);
        when(repository.findById(DOC_ID)).thenReturn(exists ? Optional.of(doc) : Optional.empty());
    }

    private DocumentoDTO metadata() {
        DocumentoDTO request = new DocumentoDTO();
        request.setTitulo("Reglamento");
        request.setCategoria("REGLAMENTO_INTERNO");
        return request;
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "reglamento.pdf", "application/pdf", new byte[]{1, 2, 3});
    }
}

package com.saed.backend.common.service;

import com.saed.backend.common.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageServiceImpl(tempDir.toString());
    }

    @Test
    void store_ValidPdf_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "soporte",
                "factura_123.pdf",
                "application/pdf",
                "%PDF-1.4 Mock PDF Content".getBytes()
        );

        FileStorageService.StoredFile stored = fileStorageService.store(file, "gastos");

        assertNotNull(stored);
        assertEquals("factura_123.pdf", stored.originalFilename());
        assertEquals("application/pdf", stored.mimeType());
        assertTrue(stored.sizeBytes() > 0);
        assertNotNull(stored.sha256());
        assertEquals(64, stored.sha256().length());
        assertTrue(stored.relativePath().startsWith("gastos"));

        // Load as resource
        Resource res = fileStorageService.loadAsResource(stored.relativePath());
        assertTrue(res.exists());
        assertTrue(res.isReadable());
    }

    @Test
    void store_ValidImage_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "soporte",
                "recibo_pago.png",
                "image/png",
                new byte[]{1, 2, 3, 4, 5}
        );

        FileStorageService.StoredFile stored = fileStorageService.store(file, "gastos");
        assertNotNull(stored);
        assertEquals("recibo_pago.png", stored.originalFilename());
        assertEquals("image/png", stored.mimeType());
    }

    @Test
    void store_DisallowedExtension_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "soporte",
                "malicious.exe",
                "application/octet-stream",
                "echo hack".getBytes()
        );

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.store(file, "gastos")
        );
        assertTrue(ex.getMessage().contains("Formato no permitido"));
    }

    @Test
    void store_EmptyFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "soporte",
                "vacio.pdf",
                "application/pdf",
                new byte[0]
        );

        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.store(file, "gastos")
        );
        assertTrue(ex.getMessage().contains("vacio"));
    }

    @Test
    void loadAsResource_PathTraversal_ThrowsSecurityException() {
        assertThrows(SecurityException.class, () ->
                fileStorageService.loadAsResource("../../etc/passwd")
        );
    }

    @Test
    void delete_ExistingFile_RemovesFile() {
        MockMultipartFile file = new MockMultipartFile(
                "soporte",
                "factura_temp.pdf",
                "application/pdf",
                "content".getBytes()
        );
        FileStorageService.StoredFile stored = fileStorageService.store(file, "gastos");

        Resource resBefore = fileStorageService.loadAsResource(stored.relativePath());
        assertTrue(resBefore.exists());

        fileStorageService.delete(stored.relativePath());

        assertThrows(RuntimeException.class, () ->
                fileStorageService.loadAsResource(stored.relativePath())
        );
    }
}

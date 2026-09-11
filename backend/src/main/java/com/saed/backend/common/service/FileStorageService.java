package com.saed.backend.common.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * FileStorageService - Servicio seguro de almacenamiento y recuperacion de archivos.
 * Implementa validacion de tipos MIME, restricciones de tamano, prevencion de
 * path traversal y generacion de hash criptografico SHA-256.
 */
public interface FileStorageService {

    record StoredFile(
        String relativePath,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sha256
    ) {}

    /**
     * Valida y almacena un archivo en el subdirectorio especificado.
     *
     * @param file         Archivo multipart recibido en el request
     * @param subDirectory Subdirectorio relativo (ej. "gastos", "documentos")
     * @return Metadatos del archivo almacenado
     */
    StoredFile store(MultipartFile file, String subDirectory);

    /**
     * Carga un archivo previamente almacenado como recurso de Spring.
     *
     * @param relativePath Ruta relativa del archivo devuelta al almacenarlo
     * @return Recurso accesible para streaming
     */
    Resource loadAsResource(String relativePath);

    /**
     * Elimina un archivo fisico si existe.
     *
     * @param relativePath Ruta relativa del archivo
     */
    void delete(String relativePath);
}

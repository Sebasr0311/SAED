package com.saed.backend.common.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * FileStorageService - Servicio seguro de almacenamiento y recuperación de archivos.
 * Implementa validación de tipos MIME, restricciones de tamaño individual, cuota
 * organizacional server-side, prevención de path traversal y generación de hash criptográfico SHA-256.
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
     * Si existe un contexto de organización activo, valida la cuota global contra su membresía.
     *
     * @param file         Archivo multipart recibido en el request
     * @param subDirectory Subdirectorio relativo (ej. "gastos", "documentos")
     * @return Metadatos del archivo almacenado
     */
    StoredFile store(MultipartFile file, String subDirectory);

    /**
     * Valida la cuota organizacional explícita y almacena un archivo en el subdirectorio especificado.
     *
     * @param file           Archivo multipart recibido en el request
     * @param subDirectory   Subdirectorio relativo (ej. "gastos", "documentos")
     * @param organizationId Identificador de la organización propietaria
     * @return Metadatos del archivo almacenado
     */
    StoredFile store(MultipartFile file, String subDirectory, Long organizationId);

    /**
     * Valida el reemplazo de un archivo existente acreditando el tamaño anterior
     * (used - oldFileBytes + newFileBytes <= limit) y almacena el nuevo archivo.
     *
     * @param file           Nuevo archivo multipart
     * @param subDirectory   Subdirectorio relativo
     * @param organizationId Identificador de la organización propietaria
     * @param oldFileBytes   Tamaño en bytes del archivo anterior que se reemplaza
     * @return Metadatos del nuevo archivo almacenado
     */
    StoredFile storeReplacement(MultipartFile file, String subDirectory, Long organizationId, long oldFileBytes);

    /**
     * Carga un archivo previamente almacenado como recurso de Spring.
     *
     * @param relativePath Ruta relativa del archivo devuelta al almacenarlo
     * @return Recurso accesible para streaming
     */
    Resource loadAsResource(String relativePath);

    /**
     * Elimina un archivo físico si existe.
     *
     * @param relativePath Ruta relativa del archivo
     */
    void delete(String relativePath);
}

package com.saed.backend.dashboard.export;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Resultado inmutable de una operación de exportación de reporte.
 *
 * @param content   Bytes binarios del archivo generado (CSV, PDF, JSON).
 * @param filename  Nombre del archivo sugerido para descarga.
 * @param mediaType Tipo MIME del contenido.
 */
public record ExportResult(
        byte[] content,
        String filename,
        MediaType mediaType
) {
    /**
     * Construye un ResponseEntity<Resource> configurado para descarga como archivo adjunto (Content-Disposition: attachment)
     * con codificación RFC 5987 / RFC 6266 UTF-8 para compatibilidad universal con navegadores.
     *
     * @return ResponseEntity con el contenido binario y cabeceras HTTP de descarga.
     */
    public ResponseEntity<Resource> toResponseEntity() {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename != null ? filename : "export", StandardCharsets.UTF_8)
                .build();

        byte[] safeContent = content != null ? content : new byte[0];

        return ResponseEntity.ok()
                .contentType(mediaType != null ? mediaType : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(safeContent.length)
                .body(new ByteArrayResource(safeContent));
    }
}

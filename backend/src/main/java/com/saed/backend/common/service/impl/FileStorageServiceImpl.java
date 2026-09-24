package com.saed.backend.common.service.impl;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.StorageQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB individual limit
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".jpg", ".jpeg", ".png");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/jpg"
    );

    private final Path rootLocation;
    private final StorageQuotaService storageQuotaService;

    public FileStorageServiceImpl(@Value("${saed.storage.upload-dir:uploads}") String uploadDir) {
        this(uploadDir, null);
    }

    @Autowired
    public FileStorageServiceImpl(@Value("${saed.storage.upload-dir:uploads}") String uploadDir,
                                  @Lazy @Autowired(required = false) StorageQuotaService storageQuotaService) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.storageQuotaService = storageQuotaService;
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            log.error("No se pudo inicializar el directorio de almacenamiento en {}", rootLocation, e);
            throw new IllegalStateException("Error inicializando directorio de archivos", e);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String subDirectory) {
        Long contextOrgId = resolveContextOrgId();
        return store(file, subDirectory, contextOrgId);
    }

    @Override
    public StoredFile store(MultipartFile file, String subDirectory, Long organizationId) {
        validateFileConstraints(file);

        // Double validation: 2. Cumulative organizational limit check
        Long targetOrgId = organizationId != null ? organizationId : resolveContextOrgId();
        if (targetOrgId != null && storageQuotaService != null) {
            storageQuotaService.validateUpload(targetOrgId, file.getSize());
        }

        return persistFileToDisk(file, subDirectory);
    }

    @Override
    public StoredFile storeReplacement(MultipartFile file, String subDirectory, Long organizationId, long oldFileBytes) {
        validateFileConstraints(file);

        // Double validation: 2. Cumulative organizational limit check with credit for replaced file
        Long targetOrgId = organizationId != null ? organizationId : resolveContextOrgId();
        if (targetOrgId != null && storageQuotaService != null) {
            storageQuotaService.validateReplacement(targetOrgId, oldFileBytes, file.getSize());
        }

        return persistFileToDisk(file, subDirectory);
    }

    @Override
    public StoredFile storeBytes(byte[] bytes, String filename, String mimeType, String subDirectory, Long organizationId) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("El contenido del archivo no puede estar vacio");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format("El archivo excede el tamano maximo permitido de 10 MB (%d bytes)", MAX_FILE_SIZE_BYTES));
        }

        String originalFilename = StringUtils.cleanPath(filename != null && !filename.isBlank() ? filename : "documento.pdf");
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("El nombre del archivo contiene caracteres invalidos de ruta");
        }

        String extension = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            extension = originalFilename.substring(dotIdx).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Formato no permitido (" + extension + "). Solo se aceptan archivos PDF, JPG y PNG");
        }

        String contentType = (mimeType != null && !mimeType.isBlank()) ? mimeType.toLowerCase() : "application/pdf";
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de contenido no valido (" + contentType + "). Solo se admiten PDF e imagenes.");
        }

        // Validación de cuota acumulativa de la organización
        Long targetOrgId = organizationId != null ? organizationId : resolveContextOrgId();
        if (targetOrgId != null && storageQuotaService != null) {
            storageQuotaService.validateUpload(targetOrgId, bytes.length);
        }

        try {
            Path targetDir = rootLocation.resolve(subDirectory != null ? subDirectory : "contratos").normalize();
            if (!targetDir.startsWith(rootLocation)) {
                throw new SecurityException("Intento de directory traversal detectado");
            }
            Files.createDirectories(targetDir);

            String sanitizedBaseName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedFilename = UUID.randomUUID() + "_" + sanitizedBaseName;
            Path destinationFile = targetDir.resolve(storedFilename).normalize();

            if (!destinationFile.startsWith(rootLocation)) {
                throw new SecurityException("Intento de almacenamiento fuera del directorio permitido");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] shaBytes = digest.digest(bytes);
            String sha256Hex = HexFormat.of().formatHex(shaBytes);

            Files.write(destinationFile, bytes);

            String relativeStoredPath = rootLocation.relativize(destinationFile).toString().replace('\\', '/');

            log.info("Archivo (bytes) almacenado exitosamente: {} (tamano: {} bytes, hash: {})",
                    relativeStoredPath, bytes.length, sha256Hex);

            return new StoredFile(
                    relativeStoredPath,
                    originalFilename,
                    contentType,
                    bytes.length,
                    sha256Hex
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash no disponible", e);
        } catch (IOException e) {
            log.error("Error al guardar archivo {}", originalFilename, e);
            throw new RuntimeException("Fallo al almacenar el archivo en disco", e);
        }
    }

    private void validateFileConstraints(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo adjunto no puede estar vacio");
        }

        // Double validation: 1. Individual file limit check
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format("El archivo excede el tamano maximo permitido de 10 MB (%d bytes)", MAX_FILE_SIZE_BYTES));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "documento");
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("El nombre del archivo contiene caracteres invalidos de ruta");
        }

        String extension = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            extension = originalFilename.substring(dotIdx).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Formato no permitido (" + extension + "). Solo se aceptan archivos PDF, JPG y PNG");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Tipo de contenido no valido (" + contentType + "). Solo se admiten PDF e imagenes.");
        }
    }

    private StoredFile persistFileToDisk(MultipartFile file, String subDirectory) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "documento");
        String contentType = file.getContentType();

        try {
            Path targetDir = rootLocation.resolve(subDirectory != null ? subDirectory : "general").normalize();
            if (!targetDir.startsWith(rootLocation)) {
                throw new SecurityException("Intento de directory traversal detectado");
            }
            Files.createDirectories(targetDir);

            String sanitizedBaseName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedFilename = UUID.randomUUID() + "_" + sanitizedBaseName;
            Path destinationFile = targetDir.resolve(storedFilename).normalize();

            if (!destinationFile.startsWith(rootLocation)) {
                throw new SecurityException("Intento de almacenamiento fuera del directorio permitido");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            String sha256Hex = HexFormat.of().formatHex(digest.digest());

            try (InputStream is = file.getInputStream()) {
                Files.copy(is, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String relativeStoredPath = rootLocation.relativize(destinationFile).toString().replace('\\', '/');

            log.info("Archivo almacenado exitosamente: {} (tamano: {} bytes, hash: {})",
                    relativeStoredPath, file.getSize(), sha256Hex);

            return new StoredFile(
                    relativeStoredPath,
                    originalFilename,
                    contentType != null ? contentType.toLowerCase() : "application/octet-stream",
                    file.getSize(),
                    sha256Hex
            );

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash no disponible", e);
        } catch (IOException e) {
            log.error("Error al guardar archivo {}", originalFilename, e);
            throw new RuntimeException("Fallo al almacenar el archivo en disco", e);
        }
    }

    private Long resolveContextOrgId() {
        try {
            if (SaedContextHolder.getContext() != null) {
                return SaedContextHolder.getContext().getOrganizationId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        try {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("La ruta del archivo no puede estar vacia");
            }

            Path file = rootLocation.resolve(relativePath).normalize();
            if (!file.startsWith(rootLocation)) {
                throw new SecurityException("Intento de acceso a archivo fuera del directorio raiz");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("El archivo solicitado no existe o no se puede leer: " + relativePath);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Ruta de archivo malformada: " + relativePath, e);
        }
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path file = rootLocation.resolve(relativePath).normalize();
            if (file.startsWith(rootLocation)) {
                Files.deleteIfExists(file);
                log.info("Archivo eliminado: {}", relativePath);
            }
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo {}", relativePath, e);
        }
    }
}

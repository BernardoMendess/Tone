package com.tone.storage;

import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * RNF02 - Segurança de Arquivos Digitais:
 * Todos os patches binários e demos ficam em storage privado sem URL pública direta.
 * Downloads de patches exigem geração de Presigned URL temporária após validação de compra.
 */
public interface FileStorageService {

    StoredObject storePrivateFile(
            String folder,
            String originalFilename,
            InputStream content,
            long sizeBytes,
            String contentType
    );

    default StoredObject storePrivateFile(String folder, String originalFilename, byte[] content, String contentType) {
        return storePrivateFile(folder, originalFilename, new ByteArrayInputStream(content), content.length, contentType);
    }

    /**
     * Retorna um Resource streamável em modo local vault, ou Optional.empty() em modo S3
     * (onde o controller redireciona para uma Presigned URL do S3/MinIO).
     */
    Optional<Resource> openLocal(String storageKey);

    boolean exists(String storageKey);

    PresignedDownload generatePresignedDownloadUrl(
            String storageKey,
            String downloadFilename,
            UUID authorizedUserId,
            Duration ttl,
            boolean inline
    );

    default PresignedDownload generatePresignedDownloadUrl(
            String storageKey,
            String downloadFilename,
            UUID authorizedUserId,
            Duration ttl
    ) {
        return generatePresignedDownloadUrl(storageKey, downloadFilename, authorizedUserId, ttl, false);
    }
}

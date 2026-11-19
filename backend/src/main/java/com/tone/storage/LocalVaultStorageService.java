package com.tone.storage;

import com.tone.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RNF02 - Private Vault local com streaming via FileSystemResource e URLs pré-assinadas
 * HMAC-SHA256 de uso único (jti) e expiração curta.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalVaultStorageService implements FileStorageService, LocalPresignedTokenVerifier {

    private final Path rootDir;
    private final String baseUrl;
    private final byte[] signingSecret;
    private final Clock clock;
    private final ConcurrentHashMap<String, Long> activeTokens = new ConcurrentHashMap<>();

    public LocalVaultStorageService(
            @Value("${app.storage.local-root:./storage/private-vault}") String localRoot,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.storage.signing-secret}") String signingSecret,
            Clock clock
    ) {
        this.rootDir = Paths.get(localRoot).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao inicializar vault privado de arquivos", e);
        }
    }

    @Override
    public StoredObject storePrivateFile(
            String folder,
            String originalFilename,
            InputStream content,
            long sizeBytes,
            String contentType
    ) {
        String safeName = StorageSupport.sanitizeFilename(originalFilename);
        String key = folder + "/" + UUID.randomUUID() + "_" + safeName;
        Path target = resolveSafePath(key);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest md = StorageSupport.newSha256();
            try (DigestInputStream dis = new DigestInputStream(content, md)) {
                long written = Files.copy(dis, target, StandardCopyOption.REPLACE_EXISTING);
                String sha256 = StorageSupport.toHex(md.digest());
                return new StoredObject(key, written, sha256);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao armazenar arquivo privado: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Resource> openLocal(String storageKey) {
        Path path = resolveSafePath(storageKey);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(path));
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return false;
        return Files.exists(resolveSafePath(storageKey));
    }

    @Override
    public PresignedDownload generatePresignedDownloadUrl(
            String storageKey,
            String downloadFilename,
            UUID authorizedUserId,
            Duration ttl,
            boolean inline
    ) {
        if (!exists(storageKey)) {
            throw ApiException.notFound("Binário do patch não encontrado no armazenamento privado.");
        }
        Instant expiresAt = clock.instant().plus(ttl);
        long expiresEpochSec = expiresAt.getEpochSecond();
        String safeName = StorageSupport.sanitizeFilename(downloadFilename);
        String jti = UUID.randomUUID().toString();
        activeTokens.put(jti, expiresEpochSec);

        String rawPayload = storageKey + "|" + safeName + "|" + authorizedUserId + "|" + jti;
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(token + ":" + expiresEpochSec);

        String url = String.format(
                "%s/api/downloads/presigned?token=%s&expires=%d&sig=%s",
                baseUrl,
                URLEncoder.encode(token, StandardCharsets.UTF_8),
                expiresEpochSec,
                URLEncoder.encode(signature, StandardCharsets.UTF_8)
        );
        return new PresignedDownload(url, safeName, expiresAt, "PRIVATE_VAULT_HMAC");
    }

    @Override
    public VerifiedPresignedPayload verifyAndConsumeLocalPresignedToken(
            String token,
            long expiresEpochSec,
            String signature
    ) {
        if (token == null || signature == null || token.isBlank() || signature.isBlank()) {
            throw ApiException.forbidden("Token de download ausente ou inválido.");
        }
        long nowSec = clock.instant().getEpochSecond();
        if (nowSec > expiresEpochSec) {
            throw ApiException.forbidden("Link de download expirado. Gere uma nova URL na sua Biblioteca Digital.");
        }
        String expected = sign(token + ":" + expiresEpochSec);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Assinatura do link de download inválida.");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 4);
            if (parts.length != 4) {
                throw ApiException.forbidden("Payload do link de download corrompido.");
            }
            String jti = parts[3];
            Long validUntil = activeTokens.remove(jti);
            if (validUntil == null || nowSec > validUntil) {
                throw ApiException.forbidden("Link de download já utilizado ou expirado. Gere uma nova URL.");
            }
            return new VerifiedPresignedPayload(parts[0], parts[1], UUID.fromString(parts[2]));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.forbidden("Token de download inválido.");
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpiredTokens() {
        long nowSec = clock.instant().getEpochSecond();
        activeTokens.entrySet().removeIf(e -> nowSec > e.getValue());
    }

    private Path resolveSafePath(String storageKey) {
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw ApiException.forbidden("Caminho de armazenamento inválido.");
        }
        return resolved;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao assinar URL temporária.", e);
        }
    }
}

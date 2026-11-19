package com.tone.storage;

import java.util.UUID;

/**
 * B4 (ISP): Verificação de tokens pré-assinados exclusiva do modo Local Vault.
 */
public interface LocalPresignedTokenVerifier {

    VerifiedPresignedPayload verifyAndConsumeLocalPresignedToken(String token, long expiresEpochSec, String signature);

    record VerifiedPresignedPayload(String storageKey, String filename, UUID userId) {}
}

package com.tone.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * RNF02 - Integração Amazon S3 / MinIO com políticas de acesso privado, streaming upload e S3Presigner.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Clock clock;

    public S3StorageService(
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.region:us-east-1}") String region,
            @Value("${app.storage.s3.bucket:tone-private}") String bucket,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey,
            @Value("${app.storage.s3.path-style-access:true}") boolean pathStyle,
            Clock clock
    ) {
        this.bucket = bucket;
        this.clock = clock;

        var credentialsProvider = (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create();

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();

        var clientBuilder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Config);

        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Config);

        if (endpoint != null && !endpoint.isBlank()) {
            URI uri = URI.create(endpoint);
            clientBuilder.endpointOverride(uri);
            presignerBuilder.endpointOverride(uri);
        }

        this.s3Client = clientBuilder.build();
        this.s3Presigner = presignerBuilder.build();
        ensurePrivateBucket();
    }

    private void ensurePrivateBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (Exception ignored) {
            // Bucket may be managed externally via IaC/IAM
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

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        MessageDigest md = StorageSupport.newSha256();
        DigestInputStream dis = new DigestInputStream(content, md);
        s3Client.putObject(request, RequestBody.fromInputStream(dis, sizeBytes));
        return new StoredObject(key, sizeBytes, StorageSupport.toHex(md.digest()));
    }

    @Override
    public Optional<Resource> openLocal(String storageKey) {
        return Optional.empty();
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public PresignedDownload generatePresignedDownloadUrl(
            String storageKey,
            String downloadFilename,
            UUID authorizedUserId,
            Duration ttl,
            boolean inline
    ) {
        String safeName = StorageSupport.sanitizeFilename(downloadFilename);
        String disposition = inline ? "inline" : "attachment; filename=\"" + safeName + "\"";

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .responseContentDisposition(disposition)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return new PresignedDownload(
                presigned.url().toString(),
                safeName,
                clock.instant().plus(ttl),
                "AWS_S3_PRESIGNED"
        );
    }
}

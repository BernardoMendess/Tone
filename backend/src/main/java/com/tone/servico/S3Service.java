package com.tone.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name:tone-bucket-default}")
    private String bucketName;

    public S3Service(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    /**
     * Gera uma URL pré-assinada para fazer upload de um arquivo.
     * @param objectKey Caminho do arquivo no bucket (ex: timbres/123-456.zip)
     * @param mimeType Tipo do arquivo
     * @param expiraEmSegundos Tempo de validade do link
     * @return Presigned URL
     */
    public String gerarUrlUpload(String objectKey, String mimeType, int expiraEmSegundos) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(mimeType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiraEmSegundos))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Gera uma URL pré-assinada temporária para download de um arquivo protegido.
     * @param objectKey Caminho do arquivo no bucket
     * @param expiraEmSegundos Tempo de validade do link
     * @return Presigned URL para GET
     */
    public String gerarUrlDownload(String objectKey, int expiraEmSegundos) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiraEmSegundos))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presignedGetObjectRequest.url().toString();
    }
}

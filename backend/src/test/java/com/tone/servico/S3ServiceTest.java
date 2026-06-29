package com.tone.servico;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3Service — Testes Unitários")
class S3ServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3Service s3Service;

    private static final String OBJECT_KEY = "timbres/joao@tone.com/uuid_pack.zip";
    private static final String MIME_TYPE = "application/zip";
    private static final String URL_FAKE = "https://s3.amazonaws.com/tone-bucket/" + OBJECT_KEY + "?X-Amz-Signature=abc";

    @BeforeEach
    void configurar() throws Exception {
        // Injeta o nome do bucket via reflection (campo @Value não é injetado pelo MockitoExtension)
        var campoBucket = S3Service.class.getDeclaredField("bucketName");
        campoBucket.setAccessible(true);
        campoBucket.set(s3Service, "tone-bucket-test");
    }

    @Test
    @DisplayName("gerarUrlUpload: deve retornar URL de upload pré-assinada")
    void gerarUrlUpload_deveRetornarPresignedUrl() throws MalformedURLException {
        PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
        when(presignedPut.url()).thenReturn(new URL(URL_FAKE));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);

        String resultado = s3Service.gerarUrlUpload(OBJECT_KEY, MIME_TYPE, 900);

        assertThat(resultado).isEqualTo(URL_FAKE);
        verify(s3Presigner, times(1)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("gerarUrlDownload: deve retornar URL de download pré-assinada")
    void gerarUrlDownload_deveRetornarPresignedUrl() throws MalformedURLException {
        PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
        when(presignedGet.url()).thenReturn(new URL(URL_FAKE));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

        String resultado = s3Service.gerarUrlDownload(OBJECT_KEY, 300);

        assertThat(resultado).isEqualTo(URL_FAKE);
        verify(s3Presigner, times(1)).presignGetObject(any(GetObjectPresignRequest.class));
    }
}

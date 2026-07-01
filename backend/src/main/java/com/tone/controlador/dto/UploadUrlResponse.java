package com.tone.controlador.dto;

/**
 * Resposta retornada com a Presigned URL de upload e a chave do objeto no S3.
 */
public record UploadUrlResponse(String uploadUrl, String arquivoUri, int expiraEmSegundos) {}

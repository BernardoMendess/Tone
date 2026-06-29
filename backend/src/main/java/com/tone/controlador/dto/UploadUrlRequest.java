package com.tone.controlador.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UploadUrlRequest {

    @NotBlank(message = "O nome do arquivo é obrigatório")
    private String nomeArquivo;

    @NotNull(message = "O tamanho do arquivo é obrigatório")
    @Min(value = 1, message = "O arquivo não pode estar vazio")
    @Max(value = 1048576, message = "O tamanho máximo permitido é 1MB") // 1MB limite
    private Long tamanhoBytes;

    @NotBlank(message = "O tipo do arquivo é obrigatório")
    private String mimeType;

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public Long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(Long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}

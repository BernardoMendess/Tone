package com.tone.controlador.dto;

public class UploadUrlResponse {

    private String uploadUrl;
    private String arquivoUri;
    private int expiraEmSegundos;

    public UploadUrlResponse(String uploadUrl, String arquivoUri, int expiraEmSegundos) {
        this.uploadUrl = uploadUrl;
        this.arquivoUri = arquivoUri;
        this.expiraEmSegundos = expiraEmSegundos;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getArquivoUri() {
        return arquivoUri;
    }

    public void setArquivoUri(String arquivoUri) {
        this.arquivoUri = arquivoUri;
    }

    public int getExpiraEmSegundos() {
        return expiraEmSegundos;
    }

    public void setExpiraEmSegundos(int expiraEmSegundos) {
        this.expiraEmSegundos = expiraEmSegundos;
    }
}

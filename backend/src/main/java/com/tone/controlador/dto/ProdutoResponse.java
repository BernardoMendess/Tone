package com.tone.controlador.dto;

import com.tone.modelo.enums.StatusProduto;

import java.util.UUID;

public class ProdutoResponse {

    private UUID id;
    private StatusProduto status;
    private String mensagem;

    public ProdutoResponse(UUID id, StatusProduto status, String mensagem) {
        this.id = id;
        this.status = status;
        this.mensagem = mensagem;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StatusProduto getStatus() {
        return status;
    }

    public void setStatus(StatusProduto status) {
        this.status = status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}

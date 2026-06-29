package com.tone.controlador.dto;

import com.tone.modelo.enums.StatusProduto;

import java.util.UUID;

/**
 * Resposta retornada após o cadastro bem-sucedido de um produto.
 */
public record ProdutoResponse(UUID id, StatusProduto status, String mensagem) {}

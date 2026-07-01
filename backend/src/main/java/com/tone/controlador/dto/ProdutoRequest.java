package com.tone.controlador.dto;

import com.tone.modelo.enums.GeneroMusical;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public class ProdutoRequest {

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "A descrição é obrigatória")
    private String descricao;

    @NotNull(message = "O preço é obrigatório")
    @PositiveOrZero
    private BigDecimal preco;

    private Set<GeneroMusical> tags;

    @NotBlank(message = "A URI do arquivo é obrigatória")
    private String arquivoUri;

    private String videoUrl;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public Set<GeneroMusical> getTags() {
        return tags;
    }

    public void setTags(Set<GeneroMusical> tags) {
        this.tags = tags;
    }

    public String getArquivoUri() {
        return arquivoUri;
    }

    public void setArquivoUri(String arquivoUri) {
        this.arquivoUri = arquivoUri;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}

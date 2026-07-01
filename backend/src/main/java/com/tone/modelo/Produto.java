package com.tone.modelo;

import com.tone.modelo.enums.GeneroMusical;
import com.tone.modelo.enums.StatusProduto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criador_id", nullable = false)
    private Usuario criador;

    @NotBlank(message = "O nome do produto é obrigatório")
    @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @NotBlank(message = "A descrição do produto é obrigatória")
    @Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @NotNull(message = "O preço é obrigatório")
    @PositiveOrZero(message = "O preço não pode ser negativo")
    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @NotBlank(message = "A URI do arquivo é obrigatória")
    @Column(name = "arquivo_uri", nullable = false, length = 512)
    private String arquivoUri;

    @Column(name = "video_url", length = 512)
    private String videoUrl;

    @NotNull(message = "O status do produto é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusProduto status = StatusProduto.RASCUNHO;

    @ElementCollection(targetClass = GeneroMusical.class)
    @CollectionTable(name = "produto_tags", joinColumns = @JoinColumn(name = "produto_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    private Set<GeneroMusical> tags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public Produto() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Usuario getCriador() {
        return criador;
    }

    public void setCriador(Usuario criador) {
        this.criador = criador;
    }

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

    public StatusProduto getStatus() {
        return status;
    }

    public void setStatus(StatusProduto status) {
        this.status = status;
    }

    public Set<GeneroMusical> getTags() {
        return tags;
    }

    public void setTags(Set<GeneroMusical> tags) {
        this.tags = tags;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}

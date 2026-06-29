package com.tone.modelo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_verificacao_email")
public class TokenVerificacaoEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 36)
    private String token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    public TokenVerificacaoEmail() {}

    public TokenVerificacaoEmail(String token, Usuario usuario, LocalDateTime dataExpiracao) {
        this.token = token;
        this.usuario = usuario;
        this.dataExpiracao = dataExpiracao;
    }

    public Long getId() { return id; }

    public String getToken() { return token; }

    public Usuario getUsuario() { return usuario; }

    public LocalDateTime getDataExpiracao() { return dataExpiracao; }

    public boolean estaExpirado() {
        return LocalDateTime.now().isAfter(dataExpiracao);
    }
}

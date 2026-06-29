package com.tone.modelo;

import com.tone.modelo.enums.TipoPerfil;
import com.tone.modelo.validacao.Cpf;
import com.tone.modelo.validacao.SenhaForte;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome completo é obrigatório")
    @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank(message = "CPF é obrigatório")
    @Cpf
    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    private String cpf;

    @NotBlank(message = "Senha é obrigatória")
    @SenhaForte
    @Column(name = "senha", nullable = false)
    private String senha;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_perfil", nullable = false, length = 20)
    private TipoPerfil tipoPerfil = TipoPerfil.COMPRADOR;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = false;

    /** Conta ativa — false quando desativada por admin ou processo interno. */
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    /** Contador de tentativas de login consecutivas com falha. Resetado após login bem-sucedido. */
    @Column(name = "tentativas_login_falhas", nullable = false)
    private int tentativasLoginFalhas = 0;

    /** Data/hora até quando a conta está bloqueada por excesso de tentativas falhas. Null = não bloqueada. */
    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    public Usuario() {}

    public Long getId() { return id; }

    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public TipoPerfil getTipoPerfil() { return tipoPerfil; }
    public void setTipoPerfil(TipoPerfil tipoPerfil) { this.tipoPerfil = tipoPerfil; }

    public boolean isEmailVerificado() { return emailVerificado; }
    public void setEmailVerificado(boolean emailVerificado) { this.emailVerificado = emailVerificado; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public int getTentativasLoginFalhas() { return tentativasLoginFalhas; }
    public void setTentativasLoginFalhas(int tentativasLoginFalhas) { this.tentativasLoginFalhas = tentativasLoginFalhas; }

    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }
    public void setBloqueadoAte(LocalDateTime bloqueadoAte) { this.bloqueadoAte = bloqueadoAte; }

    /** Retorna true se a conta está temporariamente bloqueada por excesso de tentativas falhas. */
    public boolean estaBloqueado() {
        return bloqueadoAte != null && LocalDateTime.now().isBefore(bloqueadoAte);
    }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}

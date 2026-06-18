package com.tone.controlador.dto;

import com.tone.modelo.validacao.Cpf;
import com.tone.modelo.validacao.SenhaForte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CadastroRequisicao(
        @NotBlank(message = "Nome completo é obrigatório") String nomeCompleto,
        @NotBlank(message = "E-mail é obrigatório") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "CPF é obrigatório") @Cpf String cpf,
        @NotBlank(message = "Senha é obrigatória") @SenhaForte String senha
) {}

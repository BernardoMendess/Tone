package com.tone.controlador.dto;

import com.tone.modelo.enums.TipoPerfil;

import java.time.LocalDateTime;

public record UsuarioResposta(
        Long id,
        String nomeCompleto,
        String email,
        String cpf,
        TipoPerfil tipoPerfil,
        LocalDateTime dataCriacao
) {}

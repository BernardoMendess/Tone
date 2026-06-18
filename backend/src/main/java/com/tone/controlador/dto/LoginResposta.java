package com.tone.controlador.dto;

public record LoginResposta(
        String token,
        UsuarioResposta usuario
) {}

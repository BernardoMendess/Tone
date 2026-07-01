package com.tone.controlador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubcategoriaRequest(
        @NotBlank(message = "O nome da subcategoria é obrigatório")
        @Size(max = 100, message = "O nome da subcategoria deve ter no máximo 100 caracteres")
        String nome
) {}

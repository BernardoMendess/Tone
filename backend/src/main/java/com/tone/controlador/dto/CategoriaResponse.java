package com.tone.controlador.dto;

import java.util.List;

public record CategoriaResponse(
        Long id,
        String nome,
        List<SubcategoriaResponse> subcategorias
) {}

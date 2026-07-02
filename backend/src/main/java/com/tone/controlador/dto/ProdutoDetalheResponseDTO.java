package com.tone.controlador.dto;

import com.tone.modelo.enums.GeneroMusical;
import lombok.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoDetalheResponseDTO {
    private UUID id;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private String videoUrl;
    private String categoriaNome;
    private String subcategoriaNome;
    private Set<GeneroMusical> tags;
    private Long criadorId;
    private String criadorNome;
}
